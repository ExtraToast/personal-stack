# RX 7900 XTX dual-boot driver-corruption fix — handoff

Target node: **enschede-rx7900xtx-1** (Ryzen 7 5800X3D + Radeon RX 7900 XTX,
dual-boot NixOS / Windows). Booting NixOS and returning to Windows corrupts the
Windows AMD driver (DX12 dies) until a full DDU reinstall. This package contains
the diagnosed root cause, an ordered remediation plan, the NixOS module that
implements the Linux half of the fix, and the cross-OS runbook to execute it.

This is an SSH-into-the-node task: the NixOS changes are validated against live
Windows behaviour, so they cannot be confirmed from CI alone.

## What is already wired in this branch

- New module: `platform/nix/modules/services/dualboot-gpu-reset.nix`
  - Option `personalStack.dualBootGpuReset.{enable,gpuBdf,extraKernelParams}`.
  - When enabled: sets `reboot=acpi,cold` and installs a guarded, reboot-only
    systemd-shutdown hook that runs an ASIC/PCI reset on the configured GPU BDF.
- Host wiring: `platform/nix/hosts/enschede-rx7900xtx-1/default.nix` imports the
  module and sets `personalStack.dualBootGpuReset.enable = false`.

**It is intentionally disabled.** Do not just flip it on. The plan is
minimal-change-first and requires confirming the cause on this machine before
committing the Linux-side reset. Follow the runbook order.

## Execution order (summary — full detail in REMEDIATION-RUNBOOK.md)

1. **Phase 0 — recover Windows** to a known-good DX12 baseline with DDU + a clean
   offline Adrenalin reinstall. Do this before measuring anything.
2. **Phase 1 — confirm the cause.** Apply the Windows half (`powercfg /h off`,
   run `WINDOWS-HARDENING.cmd`) and run the warm-reboot vs cold-power-off
   discriminating experiment. Log results in `VALIDATION-LOG.md`.
3. **Phase 2/3 — Windows + UEFI hardening** (Fast Startup off; motherboard Fast
   Boot off; ReBAR/Above4G consistent; CSM off).
4. **Phase 4 — enable the NixOS module** only if the warm reboot still corrupts
   Windows. On the node: discover the GPU BDF (`NIXOS-BDF-DISCOVERY.md`), then in
   `hosts/enschede-rx7900xtx-1/default.nix` set
   `personalStack.dualBootGpuReset.enable = true;` and `gpuBdf = "0000:..:..0";`,
   `nixos-rebuild boot`, reboot into the new generation, and validate.
5. **Phase 5 — validate, bisect to the minimal sufficient set, document.**
   Escalation fallbacks (reset_method=2/4, aspm/runpm, rebar, alternate reboot
   vectors) go in `extraKernelParams`, one at a time. Rollback: `ROLLBACK.md`.

Behavioural mitigation available immediately, before any config: with Fast
Startup disabled, **power off (not reboot)** when switching NixOS -> Windows.

## Documents

| File | Purpose |
|---|---|
| `ROOT-CAUSE.md` | The diagnosed two-part cause + what was refuted, with evidence. |
| `PLAN.md` | Full ordered remediation plan (Phases 0–5, fallbacks, artifacts). |
| `REMEDIATION-RUNBOOK.md` | Step-by-step cross-OS execution. |
| `NIXOS-BDF-DISCOVERY.md` | How to find/verify the GPU PCI BDF for `gpuBdf`. |
| `WINDOWS-HARDENING.cmd` | Elevated Windows helper (Fast Startup off, etc.). |
| `VALIDATION-LOG.md` | Per-cycle test log template. |
| `ROLLBACK.md` | NixOS / Windows / UEFI rollback. |
| `COLLECT-FAILURE-DATA.md` | Evidence to gather if it still fails. |
| `REVIEW-NOTES.md` | Adversarial review of the module + how each finding was resolved. |
| `SOURCES.md` | Citations. |

## Safety notes

- The shutdown hook is reboot-only, requires an explicit AMD display-class BDF
  (vendor `0x1002`, class `0x03*`), times out every step, and fails open — a
  failed reset must not block reboot. It is best-effort, not a hard guarantee:
  an uninterruptible kernel write could still delay shutdown until systemd's own
  timeout. With `gpuBdf` unset the hook self-skips, so the flake stays evaluable.
- A true power-off is already the strongest reset; the hook only targets the warm
  reboot path. Do not run the hook by hand from a graphical session.
