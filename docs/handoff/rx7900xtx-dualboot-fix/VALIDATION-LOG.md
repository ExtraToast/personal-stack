# Validation Log - RX 7900 XTX Dual-Boot Remediation

Record every test cycle in this log. Do not skip fields; missing data makes bisection much harder.

---

## Fields reference

| Field | Description |
|---|---|
| Run ID | Sequential number, e.g. R01, R02 |
| Date/time | ISO 8601, e.g. 2026-06-17 14:30 |
| Windows AMD driver version | From Device Manager or AMD Software, e.g. 24.5.1 |
| Windows build | From `winver`, e.g. 22H2 22621.3672 |
| Windows hibernation/Fast Startup state | off / on |
| Windows Update driver exclusion | on / off / not set |
| BIOS/AGESA version | From UEFI setup or `dmidecode` |
| BIOS Fast Boot state | off / on |
| CSM state | off / on |
| Above 4G state | enabled / disabled |
| ReBAR/SAM state | enabled / disabled |
| GPU BDF | e.g. 0000:03:00.0 |
| NixOS generation | From `nixos-version` and `nix-env --list-generations` |
| Kernel version | From `uname -r` |
| Kernel params | Relevant params from `/proc/cmdline`, especially `reboot=` and `amdgpu.*` |
| amdgpu modprobe options | Active options from `/etc/modprobe.d/` or `modinfo amdgpu` |
| NixOS module active | yes / no |
| Transition used | warm reboot / Linux poweroff / AC-off cold power |
| ROCm/LLM workload used | describe workload or "none / idle" |
| Device Manager result | OK / Code 31 / Code 43 / warning / disabled |
| dxdiag output path | local file path |
| DX12 app result | launched+rendered / failed / not tested |
| Event Viewer notable errors | paste or "none" |
| PASS/FAIL | PASS / FAIL |
| Notes | anything notable |

---

## Log table

| Field | R01 Baseline | R02 Phase 2 only | R03 Phase 2 only | R04 Phase 2 only | R05 Phase 2 only | R06 Phase 2 only | R07 Min Ph4 | R08 Min Ph4 | R09 Min Ph4 | R10 Min Ph4 | R11 Min Ph4 | R12 Fallback A | R13 Fallback A | R14 Fallback A | R15 Fallback B | R16 Fallback B | R17 Fallback B | R18 Rollback |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Run ID | R01 | R02 | R03 | R04 | R05 | R06 | R07 | R08 | R09 | R10 | R11 | R12 | R13 | R14 | R15 | R16 | R17 | R18 |
| Date/time | | | | | | | | | | | | | | | | | | |
| Windows AMD driver version | | | | | | | | | | | | | | | | | | |
| Windows build | | | | | | | | | | | | | | | | | | |
| Win hibernation/Fast Startup | | | | | | | | | | | | | | | | | | |
| WU driver exclusion | | | | | | | | | | | | | | | | | | |
| BIOS/AGESA version | | | | | | | | | | | | | | | | | | |
| BIOS Fast Boot state | | | | | | | | | | | | | | | | | | |
| CSM state | | | | | | | | | | | | | | | | | | |
| Above 4G state | | | | | | | | | | | | | | | | | | |
| ReBAR/SAM state | | | | | | | | | | | | | | | | | | |
| GPU BDF | | | | | | | | | | | | | | | | | | |
| NixOS generation | | | | | | | | | | | | | | | | | | |
| Kernel version | | | | | | | | | | | | | | | | | | |
| Kernel params | | | | | | | | | | | | | | | | | | |
| amdgpu modprobe options | | | | | | | | | | | | | | | | | | |
| NixOS module active | no | no | no | no | no | no | yes | yes | yes | yes | yes | yes | yes | yes | yes | yes | yes | |
| Transition used | | warm | warm | warm | poweroff | poweroff | warm | warm | warm | poweroff | poweroff | warm | warm | poweroff | warm | warm | poweroff | |
| ROCm/LLM workload used | | | | | | | | | | | | | | | | | | |
| Device Manager result | | | | | | | | | | | | | | | | | | |
| dxdiag output path | | | | | | | | | | | | | | | | | | |
| DX12 app result | | | | | | | | | | | | | | | | | | |
| Event Viewer notable errors | | | | | | | | | | | | | | | | | | |
| **PASS/FAIL** | | | | | | | | | | | | | | | | | | |
| Notes | Phase 0 baseline | | | | | | first warm after module | | | | | after Ph0 redo | | | after Ph0 redo | | | |

---

## Cycle descriptions

| Cycle | Run IDs | Goal | Pass condition |
|---|---|---|---|
| Baseline (Phase 0) | R01 | Record clean known-good Windows state before any NixOS boot | Device Manager OK, AMD Software OK, DX12 renders |
| Phase 2 only (5 transitions) | R02-R06 | Confirm Fast Startup-off alone is sufficient | All 5 transitions pass with no DDU needed |
| Minimal Phase 4 module (5 transitions) | R07-R11 | Confirm `reboot=acpi,cold` + hook is sufficient | 3 warm reboots + 2 poweroffs pass, no DDU needed |
| Fallback A - reset_method=2 (3 transitions) | R12-R14 | Test Navi PSP reset path after minimal module fails | All pass with no DDU needed |
| Fallback B - reset_method=4 (3 transitions) | R15-R17 | Test BACO/BAMACO path after Fallback A fails | All pass with no DDU needed |
| Fallback C - gpu_recovery/aspm/runpm (1 param/cycle, 3 transitions each) | R18-R20 (gpu_recovery), R21-R23 (aspm=0), R24-R26 (runpm=0) | Test conservative power/recovery options one at a time | All pass with no DDU needed |
| Fallback D - reboot vector (pci/efi/triple, one at a time) | R27-R29 (pci,cold), R30-R32 (efi,cold), R33-R35 (triple) | Test alternate reboot= vectors | All pass with no DDU needed |
| Fallback E - rebar=0 diagnostic (3 transitions) | R36-R38 | Test only if UEFI ReBAR/SAM testing implicated ReBAR | All pass with no DDU needed |
| Fallback F - latest kernel + firmware (3 transitions) | R39-R41 | Test kernel/firmware freshness as a separate controlled change | All pass with no DDU needed |
| Bisection removal (one lever removed per cycle) | R42+ | Remove fallbacks in reverse order to find minimal sufficient set | Each removal still passes; stop at smallest set that holds |
| Rollback verification | last | Confirm rollback to previous generation restores expected behavior | NixOS previous generation active, Windows still OK |

To avoid widening the table above for every extra run, use the copyable per-run
template below for Fallback C-F, bisection-removal, and any other controlled run.

---

## Per-run template (copy one block per additional test cycle)

```text
Run ID:                          (e.g. R18 - Fallback C gpu_recovery)
Date/time:
Windows AMD driver version:
Windows build:
Win hibernation/Fast Startup:    (expect: off / unavailable)
WU driver exclusion:             (on / off)
BIOS/AGESA version:
BIOS Fast Boot state:            (expect: off)
CSM state:                       (expect: off)
Above 4G state:
ReBAR/SAM state:
GPU BDF:
NixOS generation:
Kernel version:
Kernel params:                   (exact reboot=... and amdgpu.* under test)
amdgpu modprobe options:
NixOS module active:             (yes/no)
Transition used:                 (warm reboot / Linux poweroff / AC-off cold power)
ROCm/LLM workload used:
Device Manager result:
dxdiag output path:
DX12 app result:
Event Viewer notable errors:
PASS/FAIL:
Notes:                           (which single lever changed vs previous run)
```

---

## Quick-fill commands

Run these on NixOS before each test cycle to capture the relevant state:

```sh
# Kernel params
tr ' ' '\n' < /proc/cmdline | grep -E '^reboot=|^amdgpu\.'

# NixOS version and generation
nixos-version
nix-env --list-generations --profile /nix/var/nix/profiles/system | tail -5

# Kernel version
uname -r

# Hook present and executable
test -x /etc/systemd/system-shutdown/90-rx7900xtx-amdgpu-reset && echo "hook OK" || echo "hook MISSING"

# GPU BDF active amdgpu options
cat /sys/bus/pci/devices/0000:03:00.0/vendor   # replace BDF
cat /sys/bus/pci/devices/0000:03:00.0/class    # replace BDF
```

Run these on Windows before each test cycle:

```cmd
dxdiag /t "%USERPROFILE%\Desktop\dxdiag-rx7900xtx-test.txt"
powercfg /a
reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Power" /v HiberbootEnabled
```
