{ config, ... }:
{
  imports = [
    ../../profiles/worker.nix
    ../../profiles/utility.nix
    ../../profiles/gpu-nvidia.nix
    ../../modules/k3s/node-labels.nix
    ../../modules/services/game-streaming.nix
    ./disko.nix
  ];

  networking.hostName = "enschede-gtx-960m-1";

  # GTX 960M is Maxwell 2.0 (GM107). NVIDIA dropped Maxwell/Pascal/Volta
  # from the mainline branch at R575; the 580.x legacy branch continues
  # to support them. Without this override the default 595.x driver
  # does not bind to the GPU and nvidia-container-toolkit-cdi-generator
  # fails at activation with "NVML: Driver Not Loaded".
  hardware.nvidia.package = config.boot.kernelPackages.nvidiaPackages.legacy_580;
  personalStack.k3sNodeLabels = {
    "personal-stack/site" = "enschede";
    "personal-stack/node" = "enschede-gtx-960m-1";
    "topology.kubernetes.io/region" = "enschede";
    "personal-stack/role-k3s-worker" = "true";
    "personal-stack/role-utility-host" = "true";
    "personal-stack/capability-tailscale" = "true";
    "personal-stack/capability-lan-ingress" = "true";
    "personal-stack/capability-game-streaming" = "true";
    # capability-samba deliberately absent: the media drive (/srv/media)
    # is mounted on enschede-t1000-1, not here.
    # capability-adguard deliberately absent: AdGuard runs only on
    # enschede-t1000-1.
    "personal-stack/capability-nvidia" = "true";
    "personal-stack/gpu-vendor-nvidia" = "true";
    "personal-stack/gpu-model-gtx960m" = "true";
    "personal-stack/gpu-class-transcode" = "true";
  };
  system.stateVersion = "25.05";
}
