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

  # Dual-boot Windows AMD-driver corruption fix (Linux half). DISABLED by
  # default on purpose: booting NixOS then returning to Windows leaves Navi31
  # in a dirty warm-reboot state that Windows Fast Startup restores a stale
  # driver image against, corrupting the Windows AMD driver (DX12) until a full
  # DDU reinstall. The handoff runbook requires a one-time Windows DDU recovery
  # + `powercfg /h off`, then a warm-vs-cold discriminating experiment, BEFORE
  # committing the NixOS-side cold reset. Flip enable = true and set the verified
  # GPU BDF once that experiment confirms the warm reboot is the offender.
  #   docs/handoff/rx7900xtx-dualboot-fix/REMEDIATION-RUNBOOK.md
  personalStack.dualBootGpuReset = {
    enable = false;
    # gpuBdf = "0000:03:00.0"; # set from `lspci -Dnn` (display fn, not .1 audio)
    # extraKernelParams = [ ]; # one-at-a-time escalation fallbacks (see runbook)
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
