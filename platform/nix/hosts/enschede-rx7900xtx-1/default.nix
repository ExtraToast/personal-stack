{ ... }:
{
  imports = [
    ../../profiles/worker.nix
    ../../profiles/utility.nix
    ../../profiles/gpu-amd.nix
    ../../modules/k3s/node-labels.nix
    ../../modules/services/game-streaming-amd.nix
    ../../modules/services/ollama-rocm.nix
    ../../modules/services/dualboot-gpu-reset.nix
    ./disko.nix
  ];

  networking.hostName = "enschede-rx7900xtx-1";

  # Ryzen 7 5800X3D — 8C/16T Zen 3. Enable microcode updates and the
  # standard AMD P-state driver so the kernel can scale cores under
  # mixed game-streaming + ROCm inference load.
  hardware.cpu.amd.updateMicrocode = true;
  boot.kernelParams = [ "amd_pstate=active" ];

  # Dual-boot Windows AMD-driver corruption fix (Linux half). Booting NixOS then
  # returning to Windows left Navi31 in a dirty warm-reboot state that Windows
  # Fast Startup restored a stale driver image against, corrupting the Windows
  # AMD driver (DX12) until a full DDU reinstall. The Windows half is handled
  # (Fast Startup disabled via `powercfg /h off` + a clean DDU reinstall); this
  # forces a cold reboot vector and runs a guarded ASIC/PCI reset on the
  # Linux->Windows reboot path so the card is handed to firmware cleanly.
  # gpuBdf is the Navi31 display function from `lspci -Dnn` on this box
  # (0000:09:00.0, vendor 0x1002, class 0x030000, bound to amdgpu, writable
  # reset node). If Windows still corrupts after a warm reboot, escalate via
  # extraKernelParams one entry at a time — the reset node only exposes a `bus`
  # reset, which may be too weak for Navi31 (try "amdgpu.reset_method=2" first).
  personalStack.dualBootGpuReset = {
    enable = true;
    gpuBdf = "0000:09:00.0";
  };

  personalStack.k3sNodeLabels = {
    "personal-stack/site" = "enschede";
    "personal-stack/node" = "enschede-rx7900xtx-1";
    "topology.kubernetes.io/region" = "enschede";
    "personal-stack/role-k3s-worker" = "true";
    "personal-stack/role-utility-host" = "true";
    "personal-stack/capability-tailscale" = "true";
    "personal-stack/capability-lan-ingress" = "true";
    "personal-stack/capability-game-streaming" = "true";
    "personal-stack/capability-llm-host" = "true";
    # capability-samba deliberately absent: the media drive
    # (/srv/media) lives on enschede-t1000-1.
    # capability-adguard deliberately absent: AdGuard runs only on
    # enschede-t1000-1.
    "personal-stack/capability-amd-gpu" = "true";
    "personal-stack/gpu-vendor-amd" = "true";
    "personal-stack/gpu-model-rx7900xtx" = "true";
    "personal-stack/gpu-class-render-compute" = "true";
  };

  # This desktop is powered off frequently — it is the opportunistic
  # heavy-LLM host (host-native ROCm Ollama), not a general worker. Taint
  # it NoSchedule so no general workload lands here and gets stranded (or
  # strands a local-path PVC) when the box goes offline; the cluster must
  # stay healthy on the always-on nodes alone. Pods that genuinely belong
  # here add a matching toleration.
  personalStack.k3sNodeTaints = [
    "personal-stack/intermittent=true:NoSchedule"
  ];

  system.stateVersion = "25.05";
}
