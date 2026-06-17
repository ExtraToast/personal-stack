# Review - RX 7900 XTX Dual-Boot Remediation Implementation

> RESOLUTION (applied before this branch was cut): every finding below was fixed.
> The shipped module lives at `platform/nix/modules/services/dualboot-gpu-reset.nix`
> (not the standalone `impl/` file the line numbers below reference).
> - Critical (systemd shutdown hook install point): fixed — the hook is now
>   installed via the supported `systemd.shutdown."90-..." = pkgs.writeShellScript ...`
>   option instead of a nested `environment.etc."systemd/system-shutdown/..."`
>   entry, so it composes cleanly and does not break `nixos-rebuild`.
> - Major (fail-open overstated): the run_timeout wrapper now documents that it is
>   best-effort and cannot preempt an uninterruptible kernel write; risk noted.
> - Minor (validation log fallback rows): `VALIDATION-LOG.md` now covers fallbacks
>   C-F plus a copyable per-run template.
> - Minor (BDF reset-node wording): `NIXOS-BDF-DISCOVERY.md` now marks the reset
>   node informational/optional; vendor/class/BDF identity are the mandatory checks.
> The original verdict is preserved below for traceability.

Verdict: REQUEST-CHANGES

The implementation is faithful to the approved plan in the narrow sense: all 7 required files are present, and `rx7900xtx-dualboot-fix.nix` compares byte-for-byte identical to the Phase 4.2 fenced module in `PLAN.md`. That is not sufficient for approval. The exact approved module uses the wrong NixOS integration point for systemd shutdown hooks and is likely not buildable/composable on NixOS as delivered.

## Critical Findings

- `impl/rx7900xtx-dualboot-fix.nix:38-39` : the shutdown hook is installed as `environment.etc."systemd/system-shutdown/90-rx7900xtx-amdgpu-reset"`, but NixOS already owns `environment.etc."systemd/system-shutdown"` and generates that directory from the `systemd.shutdown` option. Current nixpkgs defines `systemd.shutdown` as the hook interface and then sets `environment.etc."systemd/system-shutdown".source = hooks "system-shutdown" cfg.shutdown`; NixOS systemd is also compiled with `SYSTEM_SHUTDOWN_PATH="/etc/systemd/system-shutdown"`. A nested `environment.etc` child under an existing generated directory does not compose cleanly: depending on evaluation/build ordering it either attempts to write into the parent hook directory source or collides with the parent target. This can break `nixos-rebuild`/`system.build.etc`, or at best bypass the intended hook composition path. Concrete fix: remove both `environment.etc...mode` and `environment.etc...text`; define the hook through `systemd.shutdown."90-rx7900xtx-amdgpu-reset" = pkgs.writeShellScript "90-rx7900xtx-amdgpu-reset" '' ... '';` and keep the same script body minus the explicit shebang. `pkgs.writeShellScript` supplies an executable script, and NixOS will link it into `/etc/systemd/system-shutdown` through the supported option.

## Major Findings

- `impl/rx7900xtx-dualboot-fix.nix:59-65,112-145` : the fail-open claim is stronger than what the hook can actually guarantee. `timeout --kill-after=1s` bounds normal user-space commands, but it cannot reliably preempt a process stuck in an uninterruptible kernel/sysfs/debugfs write during `amdgpu_gpu_recover`, driver `unbind`, or PCI `reset`. systemd-shutdown runs hooks late, waits for them before continuing, and applies its own safety timeout; unbinding/resetting the active amdgpu device can therefore still delay or hang shutdown on a wedged kernel path. Concrete fix: either document this as a residual safety risk and stop claiming a failed reset can never block reboot, or change the reset wrapper to avoid waiting indefinitely on a stuck child and consider dropping same-slot unbind from the minimal profile. The current code is best-effort, not a hard non-blocking guarantee.

## Minor Findings

- `impl/VALIDATION-LOG.md:41-82` : the artifact spec requires enough rows for baseline, Phase 2-only, minimal Phase 4, each fallback, and rollback tests. The table preallocates baseline, Phase 2-only, minimal Phase 4, Fallback A, Fallback B, and rollback, then tells the user to add rows for Fallback C-F. Concrete fix: add explicit columns/rows for Fallback C, D, E, F and bisection-removal tests, or switch to a repeatable per-run template that already satisfies “each fallback” without manual table surgery.

- `impl/NIXOS-BDF-DISCOVERY.md:42-46` : the note says “All four checks must look correct” immediately after listing the optional `/sys/bus/pci/devices/$GPU_BDF/reset` node. The plan says the reset node may be absent or unwritable and the hook should log/skip that step. Concrete fix: state that vendor/class/BDF identity are mandatory, while the reset node is informational and absence is acceptable.

## Checks That Passed

- Faithfulness: the module is exactly the Phase 4.2 text from the plan (`cmp` returned identical), and all seven requested implementation files exist.
- Nix string escaping: Bash `${...}` forms inside the indented Nix string are escaped with `''${...}` where needed; intended Nix interpolations for `pkgs.bash`, `lib.makeBinPath`, and `gpuBdf` are unescaped.
- NixOS option shapes: `environment.etc.<name>.mode = "0755"` is a valid option type, but the target path is the wrong one to manage directly for this hook directory.
- PATH: `lib.makeBinPath [ pkgs.bash pkgs.coreutils ]` covers `bash`, `timeout`, `cat`, `readlink`, `sleep`, and `sync` used by the hook.
- systemd-shutdown contract: the hook checks the single argument and exits except for `reboot`, matching the systemd contract of `halt`, `poweroff`, `reboot`, or `kexec`.
- Device safety checks: the placeholder guard, AMD vendor check (`0x1002`), and display-class check (`0x03*`) prevent obvious wrong-device resets. They do not prove the device is specifically the RX 7900 XTX, so the BDF discovery docs remain important.
- Reset sequence shape: debugfs `amdgpu_gpu_recover` is read-triggered, the same-slot glob targets sibling functions, and the PCI reset path is guarded by `-w`.
- Windows helper: `WINDOWS-HARDENING.cmd` correctly requires elevation, runs `powercfg /h off`, runs `powercfg /a`, sets `HiberbootEnabled=0`, provides an optional driver-exclusion block with `gpupdate /force`, prints verification, and does not attempt DDU automation.
- Supporting docs: the runbook, rollback guide, and failure-data guide cover the required phases and commands at a usable level.

## Sources Checked

- Nix indented string escaping: https://nix.dev/manual/nix/2.26/language/string-literals
- NixOS `environment.etc` implementation: https://raw.githubusercontent.com/NixOS/nixpkgs/master/nixos/modules/system/etc/etc.nix
- NixOS `systemd.shutdown` option/composition: https://raw.githubusercontent.com/NixOS/nixpkgs/master/nixos/modules/system/boot/systemd.nix
- NixOS systemd shutdown path patch: https://raw.githubusercontent.com/NixOS/nixpkgs/master/pkgs/os-specific/linux/systemd/default.nix
- systemd shutdown hook contract: https://www.freedesktop.org/software/systemd/man/latest/systemd-shutdown.html
- kernel `reboot=` parameter: https://docs.kernel.org/admin-guide/kernel-parameters.html
- amdgpu module parameters/debugfs: https://docs.kernel.org/gpu/amdgpu/module-parameters.html and https://docs.kernel.org/gpu/amdgpu/debugfs.html
- Microsoft hibernation and Windows Update driver policy docs: https://learn.microsoft.com/en-us/troubleshoot/windows-client/setup-upgrade-and-drivers/disable-and-re-enable-hibernation and https://learn.microsoft.com/en-us/windows/deployment/update/waas-wufb-group-policy

## Notes

I could not run `nix-instantiate` or `nixos-rebuild` in this review container because no `nix` binary is installed. The blocking issue above is based on nixpkgs source-level validation of the relevant NixOS modules and systemd package configuration.
