# Rollback Guide - RX 7900 XTX Dual-Boot Remediation

This guide covers rolling back each layer of the remediation independently. Always record the current state before rolling back so you can re-apply if needed.

---

## NixOS rollback

### Option A: Boot previous generation from the bootloader

At the GRUB or systemd-boot menu, select the previous NixOS generation. This takes effect immediately without any shell commands and is the fastest recovery path if the new generation causes a boot failure.

### Option B: Roll back from a working NixOS shell

```sh
sudo nixos-rebuild switch --rollback
sudo systemctl reboot
```

This switches the running system to the previous generation and reboots into it.

### Option C: Remove the module import permanently

Edit `/etc/nixos/configuration.nix` and remove the module import:

```nix
# Remove or comment out this line:
./rx7900xtx-dualboot-fix.nix
```

Then rebuild and reboot:

```sh
sudo nixos-rebuild boot
sudo systemctl reboot
```

This removes `reboot=acpi,cold` from kernel params, removes the shutdown hook, and removes all `extraModprobeConfig` fallback comments from the build.

### Option D: Roll back only the last fallback (keep the rest)

If you added a fallback parameter (e.g., `amdgpu.reset_method=2`) and want to remove only that:

1. Open `/etc/nixos/rx7900xtx-dualboot-fix.nix`.
2. Remove or comment out the parameter you last added in `boot.kernelParams` or re-comment the `options amdgpu ...` line in `boot.extraModprobeConfig`.
3. Rebuild and reboot:
   ```sh
   sudo nixos-rebuild boot
   sudo systemctl reboot
   ```
4. Verify the parameter is gone:
   ```sh
   tr ' ' '\n' < /proc/cmdline | grep -E '^reboot=|^amdgpu\.'
   ```

---

## Windows rollback

Only roll back Windows changes after validation is complete or if you intentionally accept the original risk of GPU state corruption.

### Re-enable hibernation and Fast Startup

In an elevated Command Prompt or PowerShell:

```cmd
powercfg /h on
```

Then verify in Control Panel -> Power Options -> Choose what the power buttons do -> Change settings that are currently unavailable -> "Turn on fast startup" reappears and can be checked.

### Remove Windows Update driver exclusion policy

If you set `ExcludeWUDriversInQualityUpdate` during Phase 2:

```cmd
reg delete "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate /f
gpupdate /force
```

Verify the key is gone:

```cmd
reg query "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate
```

Expected: the query returns an error indicating the value does not exist.

---

## UEFI rollback

- Restore only the settings you recorded in Phase 3 before making changes.
- If you disabled motherboard Fast Boot/Ultra Fast Boot, you may re-enable it after the full validation period proves stability, but it offers no benefit and is not recommended while diagnosing this issue.
- Do not toggle ReBAR/Above 4G repeatedly outside named diagnostic runs.
- If you changed PCIe link speed or other PCIe settings experimentally, restore the values from your Phase 3 records.
- If you updated BIOS/AGESA, a downgrade is board-vendor-specific and sometimes not supported. Check the vendor's firmware downgrade policy before attempting it.

---

## Rollback order recommendation

Roll back in reverse order of installation:

1. Remove any active NixOS fallback parameters (Option D above).
2. Remove the NixOS module entirely (Option C) if the fallbacks were not helping.
3. Roll back Windows Update driver exclusion policy (if set).
4. Roll back Windows hibernation only after full validation, not during testing.
5. Restore UEFI settings only after all other rollbacks are verified stable.

Never roll back multiple layers in a single test cycle. One change at a time makes it clear which layer was responsible for any change in behavior.
