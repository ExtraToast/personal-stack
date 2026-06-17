# NixOS GPU BDF Discovery for RX 7900 XTX

The NixOS module `rx7900xtx-dualboot-fix.nix` requires you to set `gpuBdf` to the exact PCI Bus:Device.Function address of your RX 7900 XTX display function. The module intentionally does not auto-detect the device because resetting the wrong PCI device is the primary Linux-side safety risk in this remediation.

---

## Step 1: List AMD PCI devices

```sh
nix shell nixpkgs#pciutils -c lspci -Dnn | grep -Ei 'vga|display|3d|audio|1002:'
```

Expected output shape for a system with the RX 7900 XTX as the only GPU:

```
0000:03:00.0 VGA compatible controller [0300]: Advanced Micro Devices, Inc. [AMD/ATI] Navi 31 [Radeon RX 7900 XT/7900 XTX] (rev c8) [1002:744c]
0000:03:00.1 Audio device [0403]: Advanced Micro Devices, Inc. [AMD/ATI] Navi 31 HDMI/DP Audio [1002:ab30]
```

The first line (`.0` function, class `[0300]` VGA compatible controller) is the display function. That BDF — `0000:03:00.0` in this example — is `gpuBdf`.

The second line (`.1` function, class `[0403]` audio device) is the HDMI/DP audio sibling. **Do not use the audio function as `gpuBdf`.**

---

## Step 2: Cross-check via sysfs

Substitute your actual BDF for `0000:03:00.0` below.

```sh
GPU_BDF=0000:03:00.0

# Must be 0x1002 (AMD vendor ID)
cat /sys/bus/pci/devices/$GPU_BDF/vendor

# Must start with 0x03 (display class: 0x030000 VGA, 0x030200 3D controller)
cat /sys/bus/pci/devices/$GPU_BDF/class

# Shows "amdgpu" when the driver is loaded normally
readlink -f /sys/bus/pci/devices/$GPU_BDF/driver || true

# Presence and writability of this node determines whether the hook can issue a PCI reset
ls -l /sys/bus/pci/devices/$GPU_BDF/reset || true
```

The first three checks are **mandatory** before you put the BDF in the module:
the vendor must be `0x1002`, the class must start with `0x03`, and `driver`
should resolve to `amdgpu` at runtime. The fourth check (the `reset` node) is
**informational only** — it may legitimately be absent or non-writable on some
boards. The shutdown hook logs and skips the PCI-reset step in that case (the
`reboot=acpi,cold` path and the documented fallbacks still apply), so a missing
`reset` node is acceptable and is not a reason to withhold the BDF.

---

## Step 3: Handle multiple AMD GPUs

If the system has both an AMD iGPU (common on AMD Ryzen APU platforms) and the discrete RX 7900 XTX, `lspci` will show two or more AMD display functions. You must identify the RX 7900 XTX specifically.

Ways to distinguish them:

- The PCI device ID `744c` corresponds to Navi 31 / RX 7900 XTX series. The iGPU will have a different device ID.
- The slot domain/bus number in the BDF often differs (e.g., iGPU on bus `00`, discrete on bus `03`).
- Check `/sys/bus/pci/devices/<BDF>/subsystem_device` and `/sys/bus/pci/devices/<BDF>/subsystem_vendor` to match the discrete card.
- `lspci -Dnnvv -s <BDF>` shows the full details including memory BAR sizes; the discrete GPU will have much larger BARs (8 GB VRAM mapped region).

The module skips reset entirely if the BDF placeholder `REPLACE_WITH_GPU_BDF` is not replaced. This is intentional: an ambiguous or wrong BDF is worse than no reset.

---

## What the module does with the BDF

At shutdown (reboot only), the hook:

1. Validates that the configured BDF has vendor `0x1002` (AMD) and class `0x03*` (display). If either check fails, it exits without touching the hardware.
2. Triggers the amdgpu debugfs recovery node for the matching DRM card, if available.
3. Unbinds all AMD functions in the same PCI slot (display + audio sibling).
4. Issues a PCI reset via `/sys/bus/pci/devices/<BDF>/reset` if that node is writable.

All operations have a 3-second timeout and fail-open behavior: a failed unbind or reset logs a message and does not block the reboot.
