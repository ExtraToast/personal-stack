# Evidence Collection Guide - RX 7900 XTX Dual-Boot Persistent Failures

Collect this evidence before making further changes if corruption persists after Phase 2 plus the minimal Phase 4 module and controlled fallbacks. Having the data reduces the diagnostic round-trip significantly.

---

## NixOS evidence

### Prior-boot kernel log (the most important single artifact)

```sh
journalctl -b -1 -k --no-pager > ~/amdgpu-previous-boot.log
```

This captures the full kernel log from the boot before the current one (the NixOS-to-Windows transition boot). Save the file.

### Hook-specific log lines from the prior boot

```sh
journalctl -b -1 -k -g 'rx7900xtx-amdgpu-reset|amdgpu|psp|smu|dmcub|ring|reset|gpu_recover|pcie' --no-pager
```

Look for:
- `rx7900xtx-amdgpu-reset:` lines showing whether the hook ran, which steps completed, and whether any timed out.
- `amdgpu` lines around shutdown for ring hangs, PSP timeouts, or reset events.
- `pcie` lines for PCIe link training errors.

Note: absence of `rx7900xtx-amdgpu-reset:` lines does not mean the hook did not run if persistent journald (`Storage=persistent` in `/etc/systemd/journald.conf`) is not configured.

### NixOS version and generation

```sh
nixos-version
nix-env --list-generations --profile /nix/var/nix/profiles/system
```

### Kernel version

```sh
uname -a
```

### GPU PCI device details

Replace `<GPU_BDF>` with the actual BDF (e.g., `0000:03:00.0`):

```sh
nix shell nixpkgs#pciutils -c lspci -Dnnvv -s <GPU_BDF>
```

This output shows the full PCI capability list including LnkSta (PCIe link speed/width), BAR/ReBAR configuration, and reset capability bits. Save it.

### All AMD PCI devices (for cross-reference)

```sh
nix shell nixpkgs#pciutils -c lspci -Dnn | grep -Ei 'vga|display|3d|audio|1002:'
```

### Current kernel parameters and module options

```sh
cat /proc/cmdline
cat /proc/modules | grep amdgpu
modinfo amdgpu | grep -E '^parm:'
```

### Hook file verification

```sh
test -x /etc/systemd/system-shutdown/90-rx7900xtx-amdgpu-reset && echo "executable" || echo "NOT executable"
sudo bash -n /etc/systemd/system-shutdown/90-rx7900xtx-amdgpu-reset && echo "syntax OK" || echo "syntax error"
```

### sysfs state for the GPU

```sh
GPU_BDF=<GPU_BDF>   # replace with your actual BDF
cat /sys/bus/pci/devices/$GPU_BDF/vendor
cat /sys/bus/pci/devices/$GPU_BDF/class
readlink -f /sys/bus/pci/devices/$GPU_BDF/driver || true
ls -l /sys/bus/pci/devices/$GPU_BDF/reset || true
cat /sys/bus/pci/devices/$GPU_BDF/enable
```

---

## Windows evidence

### dxdiag diagnostic report

Run in an elevated or normal Command Prompt:

```cmd
dxdiag /t "%USERPROFILE%\Desktop\dxdiag-rx7900xtx-failure.txt"
```

Open the saved file and check:
- `Direct3D Acceleration:` line (should be "Enabled"; "Not Available" means failure)
- `Agp Texture Acceleration:` and `DirectDraw Acceleration:`
- Driver version and date under "Display Devices"
- Any problem codes or error notes at the bottom

### Device Manager state

In PowerShell (elevated or normal):

```powershell
Get-PnpDevice -Class Display
```

Or open Device Manager (devmgmt.msc) and look for:
- Yellow warning icon on the AMD GPU
- Error Code 31 (device not working) or Code 43 (device reported an error)
- "Disabled" state

### Installed display drivers

```cmd
pnputil /enum-drivers
```

Filter visually for AMD/ATI entries. This shows what the driver store contains and whether Windows Update added a different driver package than the one you installed manually.

### System event log - display and GPU errors

In PowerShell:

```powershell
Get-WinEvent -LogName System -MaxEvents 300 |
  Where-Object { $_.ProviderName -match 'Display|amdwddmg|amdkmdag|Kernel-PnP|WHEA' } |
  Select-Object TimeCreated, ProviderName, Id, LevelDisplayName, Message |
  Format-List
```

Save the output to a file:

```powershell
Get-WinEvent -LogName System -MaxEvents 300 |
  Where-Object { $_.ProviderName -match 'Display|amdwddmg|amdkmdag|Kernel-PnP|WHEA' } |
  Select-Object TimeCreated, ProviderName, Id, LevelDisplayName, Message |
  Format-List | Out-File "$env:USERPROFILE\Desktop\events-rx7900xtx-failure.txt"
```

Look for:
- `amdkmdag` or `amdwddmg` errors logged during or shortly after boot
- Kernel-PnP errors related to device enumeration or reset
- TDR (Timeout Detection and Recovery) events
- WHEA hardware error events

### Application event log - DX12/rendering failures

```powershell
Get-WinEvent -LogName Application -MaxEvents 100 |
  Where-Object { $_.ProviderName -match 'amd|Display|DXGI|D3D' } |
  Select-Object TimeCreated, ProviderName, Id, LevelDisplayName, Message |
  Format-List
```

---

## Evidence bundle checklist

Before reporting persistent failures, confirm you have collected:

- [ ] `~/amdgpu-previous-boot.log` (full prior-boot kernel log from NixOS)
- [ ] Hook-specific journal grep output
- [ ] `nixos-version` output
- [ ] `uname -a` output
- [ ] `lspci -Dnnvv -s <GPU_BDF>` output
- [ ] `/proc/cmdline` content
- [ ] `dxdiag-rx7900xtx-failure.txt` from Windows
- [ ] `Get-PnpDevice -Class Display` output from Windows
- [ ] `pnputil /enum-drivers` output from Windows
- [ ] System event log export with AMD/display/WHEA filter
- [ ] Completed `VALIDATION-LOG.md` rows for the failing cycles
- [ ] Phase 3 recorded UEFI settings (photos or notes)
