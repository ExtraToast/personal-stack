# RX 7900 XTX NixOS/Windows Dual-Boot Corruption Remediation Runbook

## Overview

**Root cause accepted as settled:** NixOS/amdgpu can leave the Navi31/RX 7900 XTX in a dirty power-persistent GPU state across a warm reboot. Windows Fast Startup can restore a stale kernel/display-driver image against that mutated hardware state. Once Windows is corrupted, a DDU clean reinstall is required to recover.

**Remediation strategy:** work through phases in order. Do not skip ahead. Do not stack multiple changes in a single test cycle.

References:
- Fast Startup behavior: https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/distinguishing-fast-startup-from-wake-from-hibernation
- Hibernation control: https://learn.microsoft.com/en-us/troubleshoot/windows-client/setup-upgrade-and-drivers/disable-and-re-enable-hibernation
- Kernel `reboot=` parameter: https://www.kernel.org/doc/html/latest/admin-guide/kernel-parameters.html
- AMDGPU module parameters: https://docs.kernel.org/gpu/amdgpu/module-parameters.html
- AMDGPU debugfs recovery: https://docs.kernel.org/gpu/amdgpu/debugfs.html
- systemd shutdown hooks: https://www.freedesktop.org/software/systemd/man/latest/systemd-shutdown.html
- Arch dual-boot Fast Startup warning: https://wiki.archlinux.org/title/Dual_boot_with_Windows
- DDU guide: https://www.wagnardsoft.com/content/How-use-Display-Driver-Uninstaller-DDU-Guide-Tutorial

---

## Phase 0 - One-time DDU recovery

**Goal:** restore Windows to a known-good AMD driver and DX12 baseline before any prevention test. Do not evaluate preventive levers while Windows is already corrupted.

### 0.1 Stage files while online

Download and save locally before doing anything else:

- Current stable/WHQL AMD Adrenalin installer for the RX 7900 XTX from https://www.amd.com/en/support
- Display Driver Uninstaller (DDU) from https://www.wagnardsoft.com
- BitLocker recovery key (if BitLocker is enabled on this machine)

### 0.2 Disconnect networking

Unplug Ethernet and disable Wi-Fi, or disable the adapter in Device Manager. This prevents Windows Update from injecting a display driver between DDU cleanup and the clean AMD install.

### 0.3 Boot into Safe Mode

In an elevated Command Prompt:

```cmd
shutdown /r /o /t 0
```

Then: Troubleshoot -> Advanced options -> Startup Settings -> Restart -> Safe Mode.

### 0.4 Run DDU in Safe Mode

- Device type: GPU
- Vendor: AMD
- Action: Clean and restart
- If DDU offers to prevent automatic Windows Update driver installation, enable it for the recovery window

**Do not automate DDU.** Run it interactively as described above.

### 0.5 Install clean AMD driver (offline)

After DDU restarts Windows, stay offline and install the staged AMD Adrenalin package. Choose the clean/factory reset option if AMD offers it. When prompted, use **Restart** (not Shut down).

### 0.6 Record baseline

After restarting into Windows, run:

```cmd
dxdiag /t "%USERPROFILE%\Desktop\dxdiag-rx7900xtx-baseline.txt"
```

Record and save:
- AMD driver version
- Windows build (winver)
- Motherboard model and BIOS/AGESA version
- GPU VBIOS version if visible in AMD Software
- ReBAR/SAM state
- Whether networking was disconnected throughout

**Phase 0 pass:** Device Manager shows RX 7900 XTX with no Code 31, Code 43, warning icon, or disabled state. AMD Software opens without warnings. `dxdiag` reports Direct3D acceleration enabled. A DX12 application launches and renders.

**Phase 0 fail:** DDU plus clean offline Adrenalin reinstall does not restore DX12. Stop this plan and investigate Windows installation health, driver package choice, hardware instability, or GPU failure before continuing.

---

## Phase 1 - Discriminating experiment (warm vs cold)

**Goal:** confirm on this specific machine which transition is unsafe before committing Linux configuration. Change only Windows hibernation and operator behavior in this phase; do not add the NixOS module yet.

### 1.1 Preparation

In an elevated Windows Command Prompt or PowerShell:

```cmd
powercfg /h off
powercfg /a
shutdown /r /t 0
```

Expected: `powercfg /a` reports hibernation unavailable. In Control Panel -> Power Options -> Choose what the power buttons do -> Change settings that are currently unavailable, "Turn on fast startup" is unchecked or absent.

Use the same Linux workload shape in both arms. If failures normally follow ROCm/LLM use, run a short representative session in both arms. Stop active compute jobs cleanly before rebooting.

### 1.2 Arm A - warm NixOS to Windows reboot

1. Start from the Phase 0 known-good Windows baseline.
2. Boot NixOS with the current config (no new module yet).
3. Run the representative workload, or let amdgpu initialize normally.
4. Reboot directly from NixOS:
   ```sh
   sudo systemctl reboot
   ```
5. Select Windows in the boot menu.
6. Run the Windows checks (see 1.4 below).
7. If the first cycle passes, repeat up to two more warm cycles to reduce false negatives.

### 1.3 Arm B - true cold NixOS to Windows power-off

1. If Arm A corrupted Windows, repeat Phase 0 first.
2. Boot NixOS.
3. Run the exact same workload shape used in Arm A.
4. Power off instead of rebooting:
   ```sh
   sudo systemctl poweroff
   ```
5. Wait until fans and LEDs are off.
6. Turn the PSU switch or power strip off, or remove AC, for 10-15 seconds.
7. Restore power and boot Windows.
8. Run the same Windows checks.
9. If the first cycle passes, repeat up to two more cold cycles if practical.

### 1.4 Windows checks after every arm

```cmd
dxdiag /t "%USERPROFILE%\Desktop\dxdiag-rx7900xtx-test.txt"
```

Also verify:
- Device Manager has no AMD GPU Code 31/43, warning icon, or disabled state.
- AMD Software opens normally.
- The chosen DX12 app launches and renders.

Optional event check (PowerShell):

```powershell
Get-WinEvent -FilterHashtable @{LogName='System'; StartTime=(Get-Date).AddMinutes(-20)} |
  Where-Object { $_.ProviderName -match 'Display|amdwddmg|amdkmdag|Kernel-PnP|WHEA' } |
  Select-Object TimeCreated, ProviderName, Id, LevelDisplayName, Message
```

### 1.5 Interpretation

| Result | Next step |
|---|---|
| Warm reboot fails, cold power-off passes | Complete Phases 2, 3, then deploy the Phase 4 NixOS module |
| Both pass after `powercfg /h off` | Complete Phases 2 and 3, run Phase 5 validation, keep Phase 4 ready but defer unless failures recur |
| Cold power-off fails after fresh DDU baseline | Stop stacking Linux changes; investigate Windows Update driver replacement, AMD driver version, BIOS/AGESA, hardware stability |
| Failures only after networking returns | Windows Update driver replacement is a confounder; enable Phase 2 driver exclusion before retesting |

**Fail observation:** any Code 31/43, AMD driver mismatch/corruption error, AMD Software failure, DX12 device creation failure, missing Direct3D acceleration in `dxdiag`, relevant display/TDR/amdkmdag errors tied to the boot, or DDU being required again.

---

## Phase 2 - Windows hardening

**Goal:** permanently prevent Windows from restoring a stale kernel/display-driver image.

Run `WINDOWS-HARDENING.cmd` as Administrator, or execute the commands below manually.

### 2.1 Required: disable hibernation and Fast Startup

In an elevated Command Prompt or PowerShell:

```cmd
powercfg /h off
powercfg /a
```

Expected: hibernation is unavailable; Fast Startup becomes unavailable because it depends on hibernation.

### 2.2 GUI confirmation

Control Panel -> Power Options -> Choose what the power buttons do -> Change settings that are currently unavailable -> confirm "Turn on fast startup" is unchecked or absent.

### 2.3 Registry verification

```cmd
reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Power" /v HiberbootEnabled
```

If `HiberbootEnabled` exists and is `0x1`, correct it:

```cmd
reg add "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Power" /v HiberbootEnabled /t REG_DWORD /d 0 /f
```

### 2.4 Optional: exclude drivers from Windows Update

Recommended during recovery and validation to prevent Windows Update from replacing the clean AMD driver.

Group Policy (Pro/Enterprise): `gpedit.msc` -> Computer Configuration -> Administrative Templates -> Windows Components -> Windows Update -> Manage updates offered from Windows Update -> Do not include drivers with Windows Updates -> Enabled

Registry equivalent:

```cmd
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate /t REG_DWORD /d 1 /f
gpupdate /force
```

### 2.5 Operating rules during validation

- Use **Restart** when leaving Windows for another OS.
- Do not use Hibernate.
- Do not re-enable Fast Startup.
- Install AMD display drivers manually only.
- Reconnect networking only after the clean AMD driver is installed and the driver policy is set.

### 2.6 Phase 2 rollback (only if intentionally accepting the original risk)

```cmd
powercfg /h on
reg delete "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate /f
gpupdate /force
```

Do not run these during validation.

---

## Phase 3 - UEFI checklist

**Goal:** remove firmware shortcuts and keep PCIe/GPU enumeration stable across both operating systems.

### 3.1 Record current settings first

Before changing anything, record (preferably with photos):

- Motherboard model and BIOS/AGESA version
- GPU board model and VBIOS version if visible
- Motherboard Fast Boot/Ultra Fast Boot state
- CSM state
- Above 4G Decoding state
- ReBAR/SAM state
- PCIe link speed setting if manually configured

### 3.2 Changes to apply

Apply in this order:

1. **Disable motherboard Fast Boot/Ultra Fast Boot.** This is separate from Windows Fast Startup and avoids firmware shortcut paths.
2. **Keep CSM disabled.** Both Windows and NixOS should boot in pure UEFI mode from GPT disks.
3. **Keep Above 4G Decoding and ReBAR/SAM consistent** across both OSes and all tests. Preferred starting point is the current known-good state, usually Above 4G enabled and ReBAR/SAM enabled on modern platforms.
4. **Do not toggle ReBAR/SAM** during Phases 1-4. It is a diagnostic variable, not the primary cause.
5. **Update motherboard BIOS/AGESA only as a controlled step** from the board vendor, after recording settings and checking release notes for PCIe/GPU/sleep/reset/stability fixes.
6. **Keep GPU VBIOS stock.** Do not flash unless the exact card vendor publishes a relevant update for this exact board.
7. **Avoid overclocking, undervolting, experimental PCIe link speed settings**, and non-stock GPU firmware during validation.

### 3.3 Optional Linux PCIe inspection

```sh
nix shell nixpkgs#pciutils -c lspci -Dnn | grep -Ei 'vga|display|3d|audio|1002:'
nix shell nixpkgs#pciutils -c lspci -Dnnvv -s <GPU_BDF> | grep -Ei 'LnkSta|Resizable|BAR|Region'
```

**Phase 3 pass:** settings recorded, motherboard Fast Boot off, CSM off, Above 4G/ReBAR consistent, Windows still passes DX12 checks, NixOS still sees the RX 7900 XTX.

---

## Phase 4 - NixOS module

**Goal:** make the Linux-to-Windows reboot path behave more like a clean/cold handoff.

Install this phase only after Phase 1 shows warm reboot still fails, or after a Windows-only validation pass if deterministic extra protection is desired.

### 4.1 Discover the GPU BDF

See `NIXOS-BDF-DISCOVERY.md` for the full procedure. In brief:

```sh
nix shell nixpkgs#pciutils -c lspci -Dnn | grep -Ei 'vga|display|3d|audio|1002:'
```

Use the VGA/Display/3D function (usually `.0`) as the BDF. Do not use the audio function (`.1`).

Cross-check:

```sh
GPU_BDF=0000:03:00.0   # replace with your actual BDF
cat /sys/bus/pci/devices/$GPU_BDF/vendor   # must be 0x1002
cat /sys/bus/pci/devices/$GPU_BDF/class    # must start with 0x03
readlink -f /sys/bus/pci/devices/$GPU_BDF/driver || true
```

### 4.2 Install and activate the module

Copy `rx7900xtx-dualboot-fix.nix` to `/etc/nixos/` and edit it to replace `REPLACE_WITH_GPU_BDF` with the verified BDF.

Add it to `/etc/nixos/configuration.nix`:

```nix
{
  imports = [
    ./hardware-configuration.nix
    ./rx7900xtx-dualboot-fix.nix
  ];
}
```

Build and activate with `boot` (not `switch`) so the old generation is not modified mid-session:

```sh
sudo nixos-rebuild boot
sudo systemctl reboot
```

The first reboot is only to enter the new NixOS generation. Do not treat it as a NixOS-to-Windows validation cycle.

### 4.3 Validate the module is active

After booting back into the new NixOS generation:

```sh
tr ' ' '\n' < /proc/cmdline | grep -E '^reboot=acpi,cold$'
test -x /etc/systemd/system-shutdown/90-rx7900xtx-amdgpu-reset
sudo bash -n /etc/systemd/system-shutdown/90-rx7900xtx-amdgpu-reset
GPU_BDF=0000:03:00.0   # replace with your actual BDF
cat /sys/bus/pci/devices/$GPU_BDF/vendor
cat /sys/bus/pci/devices/$GPU_BDF/class
readlink -f /sys/bus/pci/devices/$GPU_BDF/driver || true
ls -l /sys/bus/pci/devices/$GPU_BDF/reset || true
```

Do not manually run the hook with a `reboot` argument from a graphical session. It can unbind/reset the active display GPU. If manual testing is needed, do it over SSH or from a TTY with the expectation that the display can blank.

### 4.4 Escalation fallbacks (one change per failed cycle)

Only use this ladder if the minimal Phase 4 module fails after a clean Phase 0 recovery and Phase 5 validation. Repeat Phase 0 before testing each fallback.

**Fallback A - Navi mode1/PSP reset:**

```nix
boot.kernelParams = [ "reboot=acpi,cold" "amdgpu.reset_method=2" ];
```

**Fallback B - BACO/BAMACO reset (replaces Fallback A, do not combine):**

```nix
boot.kernelParams = [ "reboot=acpi,cold" "amdgpu.reset_method=4" ];
```

**Fallback C - alternate reboot vectors (one at a time, remove previous before testing next):**

```nix
boot.kernelParams = [ "reboot=pci,cold" ];
# then if needed:
boot.kernelParams = [ "reboot=efi,cold" ];
# then if needed (diagnostic only):
boot.kernelParams = [ "reboot=triple" ];
```

**Fallback D - conservative power/recovery (one parameter per validation run):**

```nix
boot.kernelParams = [ "reboot=acpi,cold" "amdgpu.gpu_recovery=1" ];
# then if needed:
boot.kernelParams = [ "reboot=acpi,cold" "amdgpu.aspm=0" ];
# then if needed:
boot.kernelParams = [ "reboot=acpi,cold" "amdgpu.runpm=0" ];
```

**Fallback E - ReBAR diagnostic (only if UEFI ReBAR testing implicates it):**

```nix
boot.kernelParams = [ "reboot=acpi,cold" "amdgpu.rebar=0" ];
```

**Fallback F - kernel and firmware freshness (separate controlled change, do not combine with reset-method changes):**

```nix
boot.kernelPackages = pkgs.linuxPackages_latest;
hardware.enableRedistributableFirmware = true;
```

---

## Phase 5 - Validation, bisection, rollback

### 5.1 Validation log

Record all fields from `VALIDATION-LOG.md` for every test cycle. See that file for the full template.

### 5.2 Validate each layer after every rebuild

Windows:

```cmd
powercfg /a
reg query "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate
```

NixOS:

```sh
tr ' ' '\n' < /proc/cmdline | grep -E '^reboot=|^amdgpu\.'
test -x /etc/systemd/system-shutdown/90-rx7900xtx-amdgpu-reset
sudo bash -n /etc/systemd/system-shutdown/90-rx7900xtx-amdgpu-reset
GPU_BDF=0000:03:00.0   # replace with your actual BDF
cat /sys/bus/pci/devices/$GPU_BDF/vendor
cat /sys/bus/pci/devices/$GPU_BDF/class
readlink -f /sys/bus/pci/devices/$GPU_BDF/driver || true
```

After a NixOS-to-NixOS reboot, check prior-boot hook logs (if persistent journald is enabled):

```sh
journalctl -b -1 -k -g 'rx7900xtx-amdgpu-reset|amdgpu|psp|smu|dmcub|reset' --no-pager
```

### 5.3 End-to-end validation cycles

| Cycle | Requirement |
|---|---|
| Baseline | Windows passes Device Manager, AMD Software, `dxdiag`, DX12 app immediately after Phase 0 |
| Phase 2 only | If Phase 1 showed warm and cold both pass, run five Linux-to-Windows transitions over normal use before adding the NixOS module |
| Minimal Phase 4 module | At least three warm NixOS-to-Windows reboots and two NixOS poweroff-to-Windows boots; include at least one ROCm/LLM session if that is normal use |
| Each fallback | After any fail, repeat Phase 0, enable exactly one fallback, rebuild, boot new generation, rerun cycles |

**Pass condition:** all Windows checks pass after required transitions, no DDU/AMD repair needed, no new boot-tied display/TDR/amdkmdag errors.

**Fail condition:** any recurrence of DDU-only Windows corruption, Code 31/43, AMD Software mismatch/repair, DX12 failure, or missing Direct3D acceleration.

### 5.4 Bisection to minimal sufficient set

1. Keep `powercfg /h off` as the baseline. It is low risk and directly addresses the cached-driver half of the cause.
2. Keep motherboard Fast Boot disabled during the entire validation period.
3. If Phase 2 alone passes five Linux-to-Windows transitions and one week of normal use, stop. Do not deploy the NixOS module unless deterministic extra protection is desired.
4. If the minimal Phase 4 module passes, keep only `reboot=acpi,cold` plus the guarded hook. Do not add fallback parameters.
5. If a fallback passes, remove extra fallbacks in reverse order, one at a time, with fresh validation after each removal:
   - Remove `amdgpu.rebar=0` first
   - Remove `amdgpu.runpm=0`
   - Remove `amdgpu.aspm=0`
   - Remove `amdgpu.gpu_recovery=1`
   - Compare `reset_method=4` vs `reset_method=2`; keep only the one that passes
   - Compare alternate `reboot=` values only if they were needed
6. Do not remove multiple levers in one test cycle.
7. Do not remove the shutdown hook until stability is proven. If testing hook removal, keep `reboot=acpi,cold` and remove only the hook.

### 5.5 Rollback

See `ROLLBACK.md` for the complete rollback guide.

### 5.6 What if it still fails?

1. **Stop warm Linux-to-Windows reboots.** Use the safe operational path:
   ```sh
   sudo systemctl poweroff
   ```
   Wait for full power-off, then use PSU/AC-off for 10-15 seconds before booting Windows.

2. Repeat Phase 0 once to clear any Windows corruption.

3. Confirm Windows Update did not replace the AMD package:
   ```cmd
   pnputil /enum-drivers
   ```

4. Test AMD driver version as a separate variable: current WHQL Adrenalin and one known-stable previous WHQL release, using DDU between versions.

5. Test newer NixOS kernel and firmware as a separate variable, not combined with multiple reset fallbacks.

6. Update motherboard BIOS/AGESA if vendor notes mention PCIe, GPU, sleep/resume, reset, USB4, or stability fixes.

7. Collect evidence before further changes. See `COLLECT-FAILURE-DATA.md` for the complete evidence collection procedure.

8. If true AC-off cold boots still corrupt Windows after DDU and Fast Startup-off, stop treating this as a warm-reset prevention problem. Investigate board PCIe reset defects, BIOS/AGESA bugs, PSU transients, marginal GPU/PCIe hardware, non-stock VBIOS, or Windows installation/driver-store integrity.
