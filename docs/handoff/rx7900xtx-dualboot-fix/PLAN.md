# Consolidated remediation PLAN - RX 7900 XTX NixOS/Windows dual-boot corruption

This is the authoritative consolidated plan for the implementer. It accepts the investigation root cause as settled: NixOS/amdgpu can leave Navi31/RX 7900 XTX in dirty power-persistent GPU state across a warm reboot, Windows Fast Startup can restore a stale kernel/display-driver image against that mutated hardware state, and once Windows is corrupted a DDU clean reinstall is required to recover.

Conflict resolutions chosen for safety:

1. The initial Linux configuration is minimal: `reboot=acpi,cold` plus a guarded reboot-only shutdown reset hook. `amdgpu.reset_method=2` is not active initially even though the synthesis combined recipe included it, because the planning brief requires minimal-change-first and one-at-a-time fallbacks.
2. The shutdown hook acts only on `reboot`. It skips `poweroff`, `halt`, and `kexec`. A real poweroff is already the strongest safe reset path, and skipping non-reboot actions reduces hang risk.
3. The hook requires an explicit RX 7900 XTX display-function BDF. It does not auto-reset an inferred device. BDF discovery is documented separately; resetting the wrong PCI device is the largest Linux-side safety risk.
4. The hook uses a best-effort reset sequence with hang avoidance: optional amdgpu debugfs recovery if present, unbind of AMD functions in the same PCI slot, then PCI reset. Every reset/unbind operation is wrapped in a short timeout and failures are logged but never block reboot.
5. ReBAR, ASPM, runtime PM, BACO, alternate reboot methods, newer kernel/firmware, and UEFI ReBAR changes are escalation or diagnostic levers only. They are not part of the first committed config.

Primary references to cite in implementation notes:

- Microsoft Fast Startup behavior: https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/distinguishing-fast-startup-from-wake-from-hibernation
- Microsoft hibernation control: https://learn.microsoft.com/en-us/troubleshoot/windows-client/setup-upgrade-and-drivers/disable-and-re-enable-hibernation
- Microsoft Windows Update driver exclusion policy: https://learn.microsoft.com/en-us/windows/deployment/update/waas-wufb-group-policy
- Kernel `reboot=` parameter: https://www.kernel.org/doc/html/latest/admin-guide/kernel-parameters.html
- AMDGPU module parameters: https://docs.kernel.org/gpu/amdgpu/module-parameters.html
- AMDGPU debugfs recovery interface: https://docs.kernel.org/gpu/amdgpu/debugfs.html
- systemd shutdown hooks: https://www.freedesktop.org/software/systemd/man/latest/systemd-shutdown.html
- Arch dual-boot Fast Startup warning: https://wiki.archlinux.org/title/Dual_boot_with_Windows
- DDU guide: https://www.wagnardsoft.com/content/How-use-Display-Driver-Uninstaller-DDU-Guide-Tutorial
- AMD cleanup/install guidance: https://www.amd.com/en/resources/support-articles/faqs/GPU-601.html and https://www.amd.com/en/resources/support-articles/faqs/RS-INSTALL.html

## Phase 0 - one-time DDU recovery

Goal: restore Windows to a known-good AMD driver and DX12 baseline before any prevention test. Do not evaluate any preventive lever while Windows is already corrupted.

1. In Windows, while still online, download and stage locally:
   - Current stable/WHQL AMD Adrenalin installer for the RX 7900 XTX.
   - Display Driver Uninstaller from Wagnardsoft.
   - BitLocker recovery key if BitLocker is enabled.

2. Disconnect networking before cleanup:
   - Unplug Ethernet and disable Wi-Fi, or disable the adapter.
   - Purpose: prevent Windows Update from injecting a display driver between DDU and the clean AMD install.

3. Boot Windows Safe Mode:
   ```cmd
   shutdown /r /o /t 0
   ```
   Then choose Troubleshoot -> Advanced options -> Startup Settings -> Restart -> Safe Mode.

4. Run DDU in Safe Mode:
   - Device type: GPU.
   - Vendor: AMD.
   - Action: Clean and restart.
   - If DDU offers to prevent automatic Windows Update driver installation, enable it for the recovery window.

5. After restart, stay offline and install the staged AMD Adrenalin package:
   - Choose the clean/factory reset option if AMD offers it.
   - Restart Windows when prompted. Use Restart, not Shut down.

6. Establish and record the clean baseline before booting NixOS again:
   - Device Manager shows RX 7900 XTX with no Code 31, Code 43, warning icon, or disabled state.
   - AMD Software opens without driver mismatch, repair, or timeout warnings.
   - `dxdiag` reports Direct3D acceleration enabled and expected DX feature levels.
   - A repeatable DX12 application, game, or benchmark launches and reaches a rendered scene.
   - Save a baseline report:
     ```cmd
     dxdiag /t "%USERPROFILE%\Desktop\dxdiag-rx7900xtx-baseline.txt"
     ```
   - Record AMD driver version, Windows build, motherboard BIOS/AGESA version, GPU VBIOS if visible, ReBAR/SAM state, and whether networking was disconnected.

Phase 0 pass: Windows is clean and DX12 works before any NixOS boot.

Phase 0 fail: DDU plus clean offline Adrenalin reinstall does not restore DX12. Stop this plan and investigate Windows installation health, driver package choice, hardware instability, or GPU failure before prevention work.

## Phase 1 - discriminating experiment

Goal: confirm on this machine which transition is unsafe before committing Linux configuration. This phase changes only Windows hibernation/Fast Startup and operator behavior; do not add the NixOS module yet.

Preparation in elevated Windows Command Prompt or PowerShell:

```cmd
powercfg /h off
powercfg /a
shutdown /r /t 0
```

Expected: `powercfg /a` reports hibernation unavailable or not enabled. In Control Panel -> Power Options -> Choose what the power buttons do -> Change settings that are currently unavailable, "Turn on fast startup" is unchecked or absent.

Use the same Linux workload shape in both arms. If the real failure usually follows ROCm/LLM use, run the same short representative ROCm/LLM workload in both arms. Stop active compute jobs cleanly before rebooting.

Arm A - warm NixOS to Windows reboot:

1. Start from the Phase 0 known-good Windows baseline.
2. Boot NixOS with the current config.
3. Run the representative workload, or at least let amdgpu initialize normally.
4. Reboot directly from NixOS:
   ```sh
   sudo systemctl reboot
   ```
5. Select Windows in the boot menu.
6. Run the Windows checks below.
7. If the first cycle passes, repeat up to two more warm cycles to reduce false negatives.

Arm B - true cold NixOS to Windows power-off:

1. If Arm A corrupted Windows, repeat Phase 0 first. A corrupted Windows state invalidates the cold comparison.
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

Windows checks after every arm:

```cmd
dxdiag /t "%USERPROFILE%\Desktop\dxdiag-rx7900xtx-test.txt"
```

Also verify:

- Device Manager has no AMD GPU Code 31/43, warning icon, or disabled state.
- AMD Software opens normally.
- The chosen DX12 app launches and renders.
- Optional event check:
  ```powershell
  Get-WinEvent -FilterHashtable @{LogName='System'; StartTime=(Get-Date).AddMinutes(-20)} |
    Where-Object { $_.ProviderName -match 'Display|amdwddmg|amdkmdag|Kernel-PnP|WHEA' } |
    Select-Object TimeCreated, ProviderName, Id, LevelDisplayName, Message
  ```

Interpretation:

- Warm reboot fails and cold power-off passes: the dirty warm GPU handoff is confirmed locally. Complete Phase 2 and Phase 3, then install the Phase 4 minimal NixOS module.
- Both warm reboot and cold power-off pass after `powercfg /h off`: Windows Fast Startup may be the only required preventive lever. Complete Phase 2 and Phase 3, then run Phase 5 validation before adding the NixOS module. Keep Phase 4 ready but do not deploy it unless the user wants deterministic extra protection or failures recur.
- Cold power-off fails too after a fresh DDU baseline: stop stacking Linux changes. Re-check Windows Update driver replacement, AMD driver version, BIOS/AGESA, hardware stability, PSU/GPU health, and non-stock VBIOS.
- Failures occur only after networking returns: treat Windows Update driver replacement as a confounder and enable the optional Phase 2 driver exclusion before retesting.

Exact fail observation: any Code 31/43, AMD driver mismatch/corruption error, AMD Software failure, DX12 device creation failure, missing Direct3D acceleration in `dxdiag`, relevant display/TDR/amdkmdag errors tied to the boot, or DDU being required again.

## Phase 2 - Windows

Goal: permanently prevent Windows from restoring a stale kernel/display-driver image and reduce recovery confounders.

Required hardening, elevated Command Prompt or PowerShell:

```cmd
powercfg /h off
powercfg /a
```

Expected: hibernation is unavailable; Fast Startup is unavailable because hibernation is off.

GUI confirmation:

1. Control Panel -> Power Options -> Choose what the power buttons do.
2. Click Change settings that are currently unavailable.
3. Confirm "Turn on fast startup" is unchecked or absent.

Registry verification and correction if useful:

```cmd
reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Power" /v HiberbootEnabled
```

If `HiberbootEnabled` exists and is `0x1`, set it to zero:

```cmd
reg add "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Power" /v HiberbootEnabled /t REG_DWORD /d 0 /f
```

Optional but recommended during recovery and validation: exclude drivers from Windows Update.

Group Policy path on Pro/Enterprise:

```text
gpedit.msc -> Computer Configuration -> Administrative Templates -> Windows Components -> Windows Update -> Manage updates offered from Windows Update -> Do not include drivers with Windows Updates -> Enabled
```

Registry equivalent:

```cmd
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate /t REG_DWORD /d 1 /f
gpupdate /force
```

Validation-period operating rules:

- Use Restart when leaving Windows for another OS.
- Do not use Hibernate.
- Do not re-enable Fast Startup.
- Install AMD display drivers manually only.
- Reconnect networking only after the clean AMD driver is installed and the Windows Update driver policy is set or consciously declined.

Phase 2 rollback only if the user intentionally accepts the original risk:

```cmd
powercfg /h on
reg delete "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate /f
gpupdate /force
```

Do not run these rollback commands during validation.

## Phase 3 - UEFI

Goal: remove firmware shortcuts and keep PCIe/GPU enumeration stable across both operating systems.

Before changing anything, record current settings, preferably with photos:

- Motherboard model and BIOS/AGESA version.
- GPU board model and VBIOS version if visible.
- Motherboard Fast Boot/Ultra Fast Boot state.
- CSM state.
- Above 4G Decoding state.
- ReBAR/SAM state.
- PCIe link speed setting if manually configured.

Apply in this order:

1. Disable motherboard Fast Boot/Ultra Fast Boot. This is separate from Windows Fast Startup and is a low-risk way to avoid firmware shortcut paths.
2. Keep CSM disabled. Both Windows and NixOS should boot in pure UEFI mode from GPT disks.
3. Keep Above 4G Decoding and ReBAR/SAM consistent across both OSes and all tests. Preferred starting point is the current known-good state, usually Above 4G enabled and ReBAR/SAM enabled on modern platforms.
4. Do not toggle ReBAR/SAM during Phases 1-4. It is an amplifier/diagnostic variable, not the primary cause.
5. Update motherboard BIOS/AGESA only as a controlled step from the board vendor, after recording settings and checking release notes for PCIe/GPU/sleep/reset/stability fixes.
6. Keep GPU VBIOS stock. Do not flash GPU VBIOS unless the exact card vendor publishes a relevant update for this exact board.
7. Avoid overclocking, undervolting, experimental PCIe link speed settings, and non-stock GPU firmware during validation.

Phase 3 pass: firmware settings are recorded, motherboard Fast Boot is off, CSM is off, Above 4G/ReBAR are consistent, Windows still passes DX12 checks, and NixOS still sees the RX 7900 XTX.

Optional Linux inspection:

```sh
nix shell nixpkgs#pciutils -c lspci -Dnn | grep -Ei 'vga|display|3d|audio|1002:'
nix shell nixpkgs#pciutils -c lspci -Dnnvv -s <GPU_BDF> | grep -Ei 'LnkSta|Resizable|BAR|Region'
```

ReBAR diagnostic rule: only if Phase 2 plus Phase 4 still fail, run one named diagnostic pass with ReBAR/SAM disabled in UEFI, keeping Above 4G as required by the board vendor. If that changes the result, update BIOS/AGESA and retest the preferred consistent ReBAR-on configuration before accepting ReBAR-off as long-term.

## Phase 4 - NixOS module

Goal: declaratively make the Linux-to-Windows reboot path behave more like a clean/cold handoff, starting with the smallest active profile and documenting stronger fallbacks.

Install this phase only after Phase 1 shows warm reboot still fails, or after a Windows-only validation pass if the user still wants deterministic extra protection.

### 4.1 BDF discovery

Find the RX 7900 XTX display-function BDF on NixOS. Do not guess.

```sh
nix shell nixpkgs#pciutils -c lspci -Dnn | grep -Ei 'vga|display|3d|audio|1002:'
```

Expected shape:

```text
0000:03:00.0 VGA compatible controller [0300]: Advanced Micro Devices, Inc. [AMD/ATI] Navi 31 ...
0000:03:00.1 Audio device [0403]: Advanced Micro Devices, Inc. [AMD/ATI] Navi 31 HDMI/DP Audio ...
```

Use the display/VGA/3D function, usually `.0`, as `gpuBdf`. Do not use the HDMI/DP audio function as the primary BDF.

Cross-check sysfs:

```sh
GPU_BDF=0000:03:00.0
cat /sys/bus/pci/devices/$GPU_BDF/vendor
cat /sys/bus/pci/devices/$GPU_BDF/class
readlink -f /sys/bus/pci/devices/$GPU_BDF/driver || true
ls -l /sys/bus/pci/devices/$GPU_BDF/reset || true
```

Expected:

- `vendor` is `0x1002`.
- `class` starts with `0x03`, commonly `0x030000` for VGA or `0x030200` for 3D controller.
- `driver` resolves to `amdgpu` during normal Linux runtime.
- `reset` may or may not be writable; the hook logs and skips if it is unavailable.

If the system has both an AMD iGPU and the RX 7900 XTX, the explicit BDF is mandatory. The module below intentionally skips reset if the placeholder is not replaced.

### 4.2 Exact minimal module contents

Create `/etc/nixos/rx7900xtx-dualboot-fix.nix` with this exact content, replacing `REPLACE_WITH_GPU_BDF` with the verified RX 7900 XTX display-function BDF.

```nix
{ lib, pkgs, ... }:

let
  # Replace with the RX 7900 XTX VGA/Display/3D function from `lspci -Dnn`.
  # Example: "0000:03:00.0". Keep the domain prefix.
  gpuBdf = "REPLACE_WITH_GPU_BDF";
in
{
  # Minimal active profile. Do not add reset_method/power/ReBAR fallbacks until
  # the minimal profile has failed a controlled validation cycle.
  boot.kernelModules = [ "amdgpu" ];
  boot.kernelParams = [
    "reboot=acpi,cold"
  ];

  boot.extraModprobeConfig = ''
    # Fallbacks for controlled escalation only. Enable exactly one fallback at a
    # time, rebuild, reboot into the new generation, and rerun validation.
    #
    # Fallback A: Navi mode1/PSP reset path.
    # options amdgpu reset_method=2
    #
    # Fallback B: BACO/BAMACO reset path, replacing Fallback A.
    # options amdgpu reset_method=4
    #
    # Fallback C: conservative power/recovery settings, one option per cycle.
    # options amdgpu gpu_recovery=1
    # options amdgpu aspm=0
    # options amdgpu runpm=0
    #
    # Fallback D: diagnostic only if ReBAR is implicated.
    # options amdgpu rebar=0
  '';

  # systemd-shutdown passes one argument: halt, poweroff, reboot, or kexec.
  # This hook is reboot-only. A true poweroff already drops power, and kexec is
  # not the normal firmware-to-Windows path.
  environment.etc."systemd/system-shutdown/90-rx7900xtx-amdgpu-reset".mode = "0755";
  environment.etc."systemd/system-shutdown/90-rx7900xtx-amdgpu-reset".text = ''
    #!${pkgs.bash}/bin/bash
    set -u

    export PATH=${lib.makeBinPath [ pkgs.bash pkgs.coreutils ]}:''${PATH:-}

    reset_timeout="3s"
    action="''${1:-}"

    case "$action" in
      reboot) ;;
      *) exit 0 ;;
    esac

    gpu_bdf="${gpuBdf}"

    log() {
      printf '<6>rx7900xtx-amdgpu-reset: %s\n' "$*" > /dev/kmsg 2>/dev/null || true
    }

    run_timeout() {
      timeout --kill-after=1s "$reset_timeout" "$@" >/dev/null 2>&1
      rc=$?
      if [ "$rc" -ne 0 ]; then
        log "command failed or timed out rc=$rc: $*"
      fi
      return 0
    }

    if [ -z "$gpu_bdf" ] || [ "$gpu_bdf" = "REPLACE_WITH_GPU_BDF" ]; then
      log "GPU BDF placeholder was not replaced; skipping reset"
      exit 0
    fi

    dev="/sys/bus/pci/devices/$gpu_bdf"
    if [ ! -d "$dev" ]; then
      log "configured GPU BDF $gpu_bdf is not present; skipping reset"
      exit 0
    fi

    vendor="$(cat "$dev/vendor" 2>/dev/null || true)"
    class="$(cat "$dev/class" 2>/dev/null || true)"

    if [ "$vendor" != "0x1002" ]; then
      log "configured device $gpu_bdf vendor $vendor is not AMD; skipping reset"
      exit 0
    fi

    case "$class" in
      0x03*) ;;
      *)
        log "configured device $gpu_bdf class $class is not display-class; skipping reset"
        exit 0
        ;;
    esac

    log "starting reboot reset sequence for $gpu_bdf"

    # Step 1: while amdgpu is still bound, ask amdgpu's own recovery/reset path
    # to run if debugfs is mounted and exposes the node. Reading the node is
    # documented to trigger recovery on supported kernels. If absent, skip.
    for card in /sys/class/drm/card[0-9] /sys/class/drm/card[0-9][0-9]; do
      [ -e "$card/device" ] || continue
      target="$(readlink -f "$card/device" 2>/dev/null || true)"
      card_bdf="''${target##*/}"
      [ "$card_bdf" = "$gpu_bdf" ] || continue

      card_name="''${card##*/}"
      card_idx="''${card_name#card}"
      recover="/sys/kernel/debug/dri/$card_idx/amdgpu_gpu_recover"

      if [ -r "$recover" ]; then
        log "triggering amdgpu recovery via $recover"
        run_timeout bash -c 'cat "$1" >/dev/null' _ "$recover"
        sleep 1
      else
        log "amdgpu recovery node unavailable for $card_name"
      fi
    done

    # Step 2: unbind AMD functions in the same PCI slot, normally the display
    # function plus HDMI/DP audio. This avoids carrying Linux-bound function
    # state into the firmware/Windows handoff. Do not rescan during shutdown.
    slot="''${gpu_bdf%.*}"
    for fn in /sys/bus/pci/devices/"$slot".*; do
      [ -e "$fn" ] || continue
      bdf="''${fn##*/}"
      fn_vendor="$(cat "$fn/vendor" 2>/dev/null || true)"
      [ "$fn_vendor" = "0x1002" ] || continue

      if [ -L "$fn/driver" ]; then
        driver_target="$(readlink -f "$fn/driver" 2>/dev/null || true)"
        driver="''${driver_target##*/}"
        log "unbinding $bdf from $driver"
        run_timeout bash -c 'printf "%s" "$1" > "$2/driver/unbind"' _ "$bdf" "$fn"
      fi
    done

    sync
    sleep 1

    # Step 3: request the kernel PCI reset attribute for the display function if
    # the platform exposes it. This may not be a full ASIC power loss on every
    # board; that is why the reboot= cold path and fallbacks exist.
    if [ -w "$dev/reset" ]; then
      log "issuing PCI reset for $gpu_bdf"
      run_timeout bash -c 'printf "1" > "$1/reset"' _ "$dev"
      sleep 1
    else
      log "no writable PCI reset node for $gpu_bdf"
    fi

    log "reset sequence complete; continuing reboot"
    exit 0
  '';
}
```

### 4.3 Import and activate

Add the module to `/etc/nixos/configuration.nix`:

```nix
{
  imports = [
    ./hardware-configuration.nix
    ./rx7900xtx-dualboot-fix.nix
  ];
}
```

For the first activation, prefer `boot` rather than `switch` so the old live generation is not modified mid-session. The first reboot is only to enter the new NixOS generation; do not treat it as a NixOS-to-Windows validation cycle.

```sh
sudo nixos-rebuild boot
sudo systemctl reboot
```

After booting back into the new NixOS generation, verify:

```sh
tr ' ' '\n' < /proc/cmdline | grep -E '^reboot=acpi,cold$'
test -x /etc/systemd/system-shutdown/90-rx7900xtx-amdgpu-reset
sudo bash -n /etc/systemd/system-shutdown/90-rx7900xtx-amdgpu-reset
GPU_BDF=0000:03:00.0
cat /sys/bus/pci/devices/$GPU_BDF/vendor
cat /sys/bus/pci/devices/$GPU_BDF/class
readlink -f /sys/bus/pci/devices/$GPU_BDF/driver || true
ls -l /sys/bus/pci/devices/$GPU_BDF/reset || true
```

Replace `0000:03:00.0` with the real BDF.

Do not manually run the hook with a `reboot` argument from a graphical session. It can unbind/reset the active display GPU. If manual testing is absolutely required, do it over SSH or from a TTY with the expectation that the display can blank.

### 4.4 Escalation fallbacks, one change per failed cycle

Only use this ladder if the minimal Phase 4 module fails after a clean Phase 0 recovery and repeatable Phase 5 validation. Clear any newly corrupted Windows state with Phase 0 before testing the next fallback.

1. Verify the basics before adding parameters:
   - `gpuBdf` is the RX 7900 XTX display function, not the audio function or iGPU.
   - `/proc/cmdline` contains exactly one `reboot=` value.
   - The hook file is executable and syntax-valid.
   - Prior-boot logs show `rx7900xtx-amdgpu-reset` entries if persistent journal is enabled.

2. Fallback A - force Navi mode1/PSP reset:
   ```nix
   boot.kernelParams = [
     "reboot=acpi,cold"
     "amdgpu.reset_method=2"
   ];
   ```
   If kernel command-line module parameters do not take on this host, use the equivalent modprobe option instead:
   ```nix
   boot.extraModprobeConfig = ''
     options amdgpu reset_method=2
   '';
   ```
   Do not set both `reset_method=2` and `reset_method=4`.

3. Fallback B - replace mode1 with BACO/BAMACO:
   ```nix
   boot.kernelParams = [
     "reboot=acpi,cold"
     "amdgpu.reset_method=4"
   ];
   ```
   Try this only after mode1 fails or creates a boot/shutdown problem.

4. Fallback C - alternate reboot vectors, one at a time, keeping the hook unchanged:
   ```nix
   boot.kernelParams = [ "reboot=pci,cold" ];
   ```
   Then, if needed:
   ```nix
   boot.kernelParams = [ "reboot=efi,cold" ];
   ```
   Then, only as a last reboot-vector diagnostic:
   ```nix
   boot.kernelParams = [ "reboot=triple" ];
   ```
   Remove the previous `reboot=` value before testing the next one.

5. Fallback D - conservative amdgpu power/recovery options, one parameter per validation run:
   ```nix
   boot.kernelParams = [ "reboot=acpi,cold" "amdgpu.gpu_recovery=1" ];
   ```
   Then, if needed:
   ```nix
   boot.kernelParams = [ "reboot=acpi,cold" "amdgpu.aspm=0" ];
   ```
   Then, if needed:
   ```nix
   boot.kernelParams = [ "reboot=acpi,cold" "amdgpu.runpm=0" ];
   ```
   These may increase idle power or reduce power-management behavior, so they are not first-line.

6. Fallback E - ReBAR diagnostic only:
   ```nix
   boot.kernelParams = [ "reboot=acpi,cold" "amdgpu.rebar=0" ];
   ```
   Use only if UEFI ReBAR/SAM testing suggests ReBAR amplifies the issue. Do not keep it long-term unless it is the only lever that changes the result.

7. Fallback F - kernel and firmware freshness as a separate controlled change:
   ```nix
   boot.kernelPackages = pkgs.linuxPackages_latest;
   hardware.enableRedistributableFirmware = true;
   ```
   Do not combine this with multiple reset-method or power-parameter changes in the same test.

## Phase 5 - validation, bisection, rollback, what-if-still-failing

Goal: prove the smallest sufficient fix and preserve an easy escape path.

### 5.1 Validation log fields

Every test cycle should record:

```text
Run ID:
Date/time:
Windows AMD driver version:
Windows build:
Windows hibernation/Fast Startup state:
Windows Update driver exclusion on/off:
BIOS/AGESA version:
BIOS Fast Boot state:
CSM state:
Above 4G state:
ReBAR/SAM state:
GPU BDF:
NixOS generation:
Kernel version:
Kernel params:
amdgpu modprobe options:
NixOS module active: yes/no
Transition used: warm reboot / Linux poweroff / AC-off cold power
ROCm/LLM workload used:
Device Manager result:
dxdiag output path:
DX12 app result:
Event Viewer notable errors:
PASS/FAIL:
Notes:
```

### 5.2 Validate each layer

Windows validation:

```cmd
powercfg /a
reg query "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate
```

Expected: hibernation/Fast Startup unavailable. Driver exclusion is `0x1` if the optional policy was selected.

NixOS validation after every rebuild:

```sh
tr ' ' '\n' < /proc/cmdline | grep -E '^reboot=|^amdgpu\.'
test -x /etc/systemd/system-shutdown/90-rx7900xtx-amdgpu-reset
sudo bash -n /etc/systemd/system-shutdown/90-rx7900xtx-amdgpu-reset
GPU_BDF=0000:03:00.0
cat /sys/bus/pci/devices/$GPU_BDF/vendor
cat /sys/bus/pci/devices/$GPU_BDF/class
readlink -f /sys/bus/pci/devices/$GPU_BDF/driver || true
```

After a NixOS-to-NixOS reboot, inspect prior-boot hook logs if persistent journald is enabled:

```sh
journalctl -b -1 -k -g 'rx7900xtx-amdgpu-reset|amdgpu|psp|smu|dmcub|reset' --no-pager
```

Absence of logs is not by itself failure if persistent journald is disabled; Windows pass/fail remains the decisive check.

End-to-end validation cycles:

1. Baseline: Windows passes Device Manager, AMD Software, `dxdiag`, and the DX12 app immediately after Phase 0.
2. Phase 2 only: if Phase 1 showed warm and cold both pass with Fast Startup off, run five Linux-to-Windows transitions over normal use before adding the NixOS module.
3. Minimal Phase 4 module: run at least three warm NixOS-to-Windows reboots and two NixOS poweroff-to-Windows boots. Include at least one representative ROCm/LLM session if that is part of normal use.
4. Each fallback: after any fail, repeat Phase 0, enable exactly one fallback, rebuild, boot into the new NixOS generation, and rerun the same validation cycles.

Pass condition for a lever: all Windows checks pass after the required transitions, no DDU/AMD repair is needed, and no new boot-tied display/TDR/amdkmdag errors appear.

Fail condition: any recurrence of DDU-only Windows corruption, Code 31/43, AMD Software mismatch/repair warning, DX12 device creation failure, or missing Direct3D acceleration.

### 5.3 Bisection to the minimal sufficient set

1. Keep Windows `powercfg /h off` as the baseline. It is low risk and directly addresses the cached-driver half of the cause.
2. Keep motherboard Fast Boot disabled during the entire validation period.
3. If Phase 2 alone passes five Linux-to-Windows transitions and one week of normal use, stop there. Do not deploy the NixOS module unless deterministic extra protection is desired.
4. If the minimal Phase 4 module passes, keep only `reboot=acpi,cold` plus the guarded reboot-only hook. Do not add `reset_method`, BACO, ASPM, runtime PM, GPU recovery, ReBAR, or kernel-latest changes.
5. If a fallback is required and later passes, remove extra fallbacks in reverse order, one at a time, with fresh validation after each removal:
   - Remove `amdgpu.rebar=0` first.
   - Remove `amdgpu.runpm=0`.
   - Remove `amdgpu.aspm=0`.
   - Remove `amdgpu.gpu_recovery=1`.
   - Compare `amdgpu.reset_method=4` and `amdgpu.reset_method=2`; keep only the one that passes.
   - Compare alternate `reboot=` values only if they were needed.
6. Do not remove multiple levers in one test cycle.
7. Do not remove the shutdown hook until stability is proven and the user accepts another possible DDU recovery if the failure returns. If testing hook removal, keep `reboot=acpi,cold` and remove only the hook.

### 5.4 Rollback

NixOS rollback:

- At boot, select the previous known-good NixOS generation from the bootloader.
- From a working NixOS shell:
  ```sh
  sudo nixos-rebuild switch --rollback
  ```
- To permanently remove the module, delete `./rx7900xtx-dualboot-fix.nix` from `imports`, then run:
  ```sh
  sudo nixos-rebuild boot
  sudo systemctl reboot
  ```
- To roll back only the latest fallback, remove the last added kernel parameter or re-comment the last `options amdgpu ...` line, then rebuild with `sudo nixos-rebuild boot` and reboot.

Windows rollback, only after validation is complete or if the user intentionally accepts the risk:

```cmd
powercfg /h on
reg delete "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate /f
gpupdate /force
```

UEFI rollback:

- Restore only the settings recorded in Phase 3.
- Do not re-enable motherboard Fast Boot while diagnosing this issue.
- Do not toggle ReBAR/Above 4G repeatedly outside named diagnostic runs.

### 5.5 What if it still fails?

If corruption persists after Phase 2 plus the minimal Phase 4 module and controlled fallbacks:

1. Stop warm Linux-to-Windows reboots. Use the operational safe path:
   ```sh
   sudo systemctl poweroff
   ```
   Wait for full power-off, then use PSU/AC-off for 10-15 seconds before booting Windows if needed.

2. Repeat Phase 0 once to clear any Windows corruption left by failed tests.

3. Confirm Windows Update did not replace the AMD package:
   ```cmd
   pnputil /enum-drivers
   ```

4. Test AMD driver version as a separate variable: one current WHQL Adrenalin release and one known-stable previous WHQL release, using DDU between driver versions.

5. Test newer NixOS kernel and firmware as a separate variable, not combined with multiple reset fallbacks.

6. Update motherboard BIOS/AGESA if vendor notes mention PCIe, GPU, sleep/resume, reset, USB4, or stability fixes.

7. Collect evidence before further changes:
   ```sh
   journalctl -b -1 -k --no-pager > amdgpu-previous-boot.log
   journalctl -b -1 -k -g 'rx7900xtx-amdgpu-reset|amdgpu|psp|smu|dmcub|ring|reset|gpu_recover|pcie' --no-pager
   nixos-version
   uname -a
   nix shell nixpkgs#pciutils -c lspci -Dnnvv -s <GPU_BDF>
   ```
   Windows:
   ```powershell
   Get-PnpDevice -Class Display
   dxdiag /t "$env:USERPROFILE\Desktop\dxdiag-rx7900xtx-failure.txt"
   Get-WinEvent -LogName System -MaxEvents 300 |
     Where-Object { $_.ProviderName -match 'Display|amdwddmg|amdkmdag|Kernel-PnP|WHEA' } |
     Select-Object TimeCreated, ProviderName, Id, LevelDisplayName, Message
   ```

8. If true AC-off cold boots still corrupt Windows after DDU and Fast Startup-off, stop treating this as a warm-reset prevention problem. Investigate board PCIe reset defects, BIOS/AGESA bugs, PSU transients, marginal GPU/PCIe hardware, non-stock VBIOS, or Windows installation/driver-store integrity.

## Risk/assumptions

- The root cause is accepted as settled for planning. Phase 1 confirms the minimal sufficient lever on this specific machine; it does not re-litigate the theory.
- DDU recovery is disruptive. Keep the AMD installer local and networking disconnected until the clean driver is installed.
- Disabling Windows hibernation removes Hibernate and Fast Startup. This is intentional for dual-boot prevention.
- The shutdown hook is best-effort. Debugfs recovery may be absent, and the PCI reset attribute may not equal a full ASIC power loss on every motherboard/GPU combination.
- The hook can blank/reset the active display if run manually. It is designed for the final shutdown/reboot stage only.
- Wrong BDF is the main Linux-side safety risk. The module therefore requires an explicit BDF and validates AMD vendor plus display class before reset.
- Every unbind/reset operation has a short timeout and fail-open behavior. A failed reset must not block reboot.
- `amdgpu.reset_method`, `reboot=`, ASPM, runtime PM, and ReBAR behavior are board/kernel/firmware dependent. That is why they are ordered as one-at-a-time fallbacks.
- `amdgpu.aspm=0`, `amdgpu.runpm=0`, and `amdgpu.rebar=0` can affect power, thermals, or performance. Do not keep them unless validation proves they are necessary.
- BIOS flashing has normal firmware-update risk. It is a controlled escalation, not a first-line fix.
- A normal AMD reinstall is not a valid recovery check after corruption; use DDU for a clean baseline.

## ARTIFACTS

The implementer must produce these files exactly:

1. `rx7900xtx-dualboot-fix.nix`
   - Drop-in NixOS module to place beside `configuration.nix` or in the host module directory.
   - Must contain the exact Phase 4 minimal module: `boot.kernelModules = [ "amdgpu" ];`, `boot.kernelParams = [ "reboot=acpi,cold" ];`, commented one-at-a-time `amdgpu` fallbacks, and the executable `/etc/systemd/system-shutdown/90-rx7900xtx-amdgpu-reset` hook.
   - Must require replacement of `REPLACE_WITH_GPU_BDF` with the verified RX 7900 XTX display-function BDF.

2. `REMEDIATION-RUNBOOK.md`
   - User-facing cross-OS runbook containing Phases 0-5 in order.
   - Must include DDU recovery steps, Phase 1 warm-vs-cold experiment, Windows commands, UEFI checklist, NixOS import/build commands, pass/fail criteria, bisection, rollback, and what-if-still-failing steps.

3. `WINDOWS-HARDENING.cmd`
   - Optional elevated Windows helper script.
   - Must run `powercfg /h off`, `powercfg /a`, set `HiberbootEnabled=0`, optionally set `ExcludeWUDriversInQualityUpdate=1`, run `gpupdate /force` when policy is changed, and print verification/rollback commands in comments.
   - Must not attempt DDU automation.

4. `NIXOS-BDF-DISCOVERY.md`
   - Short helper note showing how to identify the RX 7900 XTX BDF with `lspci -Dnn` and sysfs checks.
   - Must explain that the display/VGA/3D function is used as `gpuBdf`, the audio sibling is not the primary BDF, and the module intentionally does not auto-reset ambiguous devices.

5. `VALIDATION-LOG.md`
   - Table/template for every test cycle.
   - Must include the fields listed in Phase 5.1 and enough rows for baseline, Phase 2-only, minimal Phase 4, each fallback, and rollback tests.

6. `ROLLBACK.md`
   - Concise rollback guide.
   - Must cover selecting a previous NixOS generation, `sudo nixos-rebuild switch --rollback`, removing the module import, rolling back the last fallback only, Windows hibernation rollback, Windows Update driver-policy rollback, and restoring recorded UEFI settings.

7. `COLLECT-FAILURE-DATA.md`
   - Evidence collection guide for persistent failures.
   - Must include Windows Device Manager/Event Viewer/`dxdiag` commands, `pnputil /enum-drivers`, NixOS `journalctl -b -1 -k` commands, hook log grep, `nixos-version`, `uname -a`, and `lspci -Dnnvv -s <GPU_BDF>`.
