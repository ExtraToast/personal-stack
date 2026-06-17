# Key sources

Primary references behind the root cause and the fix.

## Windows Fast Startup / hibernation
- https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/distinguishing-fast-startup-from-wake-from-hibernation
- https://learn.microsoft.com/en-us/troubleshoot/windows-client/setup-upgrade-and-drivers/disable-and-re-enable-hibernation
- https://learn.microsoft.com/en-us/windows/deployment/update/waas-wufb-group-policy (driver exclusion)

## amdgpu reset / power / reboot
- https://docs.kernel.org/gpu/amdgpu/module-parameters.html (reset_method, aspm, runpm, gpu_recovery, rebar)
- https://docs.kernel.org/gpu/amdgpu/debugfs.html (amdgpu_gpu_recover)
- https://www.kernel.org/doc/html/latest/admin-guide/kernel-parameters.html (reboot= syntax)
- https://www.freedesktop.org/software/systemd/man/latest/systemd-shutdown.html (shutdown hook contract)

## AMD GPU reset-bug / persistent-state evidence
- https://forum.level1techs.com/t/rx-7900-xtx-sudden-psp-firmware-loading-failed/241359 (most on-point: ROCm-triggered, power-persistent, VBIOS reflash did not help)
- https://forum.level1techs.com/t/the-state-of-amd-rx-7000-series-vfio-passthrough-april-2024/210242
- https://www.nicksherlock.com/2020/11/working-around-the-amd-gpu-reset-bug-on-proxmox/
- https://patchwork.freedesktop.org/patch/315773/ (mode1/PSP reset for Navi)

## Windows recovery (DDU) and dual-boot hygiene
- https://www.wagnardsoft.com/content/How-use-Display-Driver-Uninstaller-DDU-Guide-Tutorial
- https://www.amd.com/en/resources/support-articles/faqs/GPU-601.html
- https://www.amd.com/en/resources/support-articles/faqs/RS-INSTALL.html
- https://wiki.archlinux.org/title/Dual_boot_with_Windows

## NixOS integration (module correctness)
- https://nix.dev/manual/nix/2.26/language/string-literals (indented-string antiquote escaping)
- nixpkgs `systemd.shutdown` option / `environment.etc` composition (nixos/modules/system/boot/systemd.nix, system/etc/etc.nix)
