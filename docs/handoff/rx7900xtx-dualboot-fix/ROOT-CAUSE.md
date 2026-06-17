# Root cause — RX 7900 XTX dual-boot Windows driver corruption

Two independent deep-research investigations (15+ and ~10 web sources each,
across AMD community, Microsoft docs, Arch Wiki, NixOS discourse, Level1Techs,
freedesktop/LKML amdgpu trackers) converged on the same conclusion.

## Symptom

`enschede-rx7900xtx-1` dual-boots NixOS (amdgpu, host-native ROCm/LLM + game
streaming) and Windows (Adrenalin, DirectX 12). After booting NixOS once and
returning to Windows, the Windows AMD driver is corrupted — DX12 stops working
and the display driver malfunctions — and the only reliable recovery is a full
DDU removal plus clean Adrenalin reinstall. A normal reboot does not self-heal.

## Cause — a two-part interaction (not a single fault)

The symptom requires BOTH halves of one interaction, which is why each isolated
hypothesis looks "plausible but unproven" in the forums:

1. **Dirty GPU on-die state across a WARM reboot (Linux side).** amdgpu programs
   Navi31's PSP / SMU-PMFW / DMCUB / power state during use, and ROCm/compute
   load exercises this further. A reboot NixOS->Windows is warm: the GPU power
   rail never drops and amdgpu's shutdown path does not reliably issue a full
   ASIC reset, so that volatile on-die state carries across into Windows.
   Evidence: AMD's "always reset the ASIC in suspend" kernel change; the AMD
   reset-bug / VFIO literature (cards "unusable until host reboot"; recovery
   needs a vendor PSP MODE1/MODE2 or BACO reset, not a plain PCI/bus reset); a
   near-exact Navi31 "PSP firmware loading failed" report where a full power
   cycle and even a VBIOS reflash did not fix it — recovery came only after
   extended idle, proving the persistent state is volatile on-die RAM, not flash.

2. **Windows Fast Startup / hybrid shutdown (Windows side).** A Windows "Shut
   down" hibernates the kernel plus loaded kernel-mode drivers into
   `hiberfil.sys` and restores that image on next boot instead of cold-
   initializing the GPU. Windows therefore brings back an `amdkmdag` image that
   is inconsistent with the Linux-mutated hardware, and the WDDM/DX12 device-
   creation path fails. (A *Restart* always cold-boots and bypasses Fast
   Startup — which predicts a Restart-vs-Shutdown asymmetry.)

3. **Why only DDU recovers (aftermath, not trigger).** The broken state then
   persists in the Windows DriverStore/FileRepository, the display-class and
   service registry keys, and the DX/shader caches. A normal reinstall re-binds
   the stale package, so only a DDU clean removal forces a genuinely fresh cold
   install.

## Refuted / low probability

- VBIOS/SPI flash corruption, or ROCm flashing persistent firmware: very low —
  a VBIOS reflash did not help in the on-point case, and amdgpu OverDrive state
  is volatile sysfs, never flashed.
- UEFI NVRAM cross-OS, or GOP/framebuffer handoff: low — boot-time only, torn
  down by both drivers.
- Resizable BAR / SAM: amplifier at most, not the primary cause — the BAR window
  is re-negotiated by firmware every boot.
- Windows Update silently swapping the AMD package: a confounder during the
  recovery window only.

## The fix family

Disable the Windows half (`powercfg /h off`) AND force a clean/cold GPU handoff
from NixOS (cold reboot vector + guarded ASIC/PCI reset on the reboot path, or
simply a true power-off when switching Linux->Windows). Confirm which lever is
actually required on this machine with the warm-vs-cold discriminating
experiment before committing config. The single most reliable behavioural
mitigation available immediately is to **power off (not reboot)** when leaving
NixOS for Windows, with Fast Startup already disabled.

See `PLAN.md` for the full ordered remediation plan and `REMEDIATION-RUNBOOK.md`
for step-by-step execution. Citations: `SOURCES.md`.
