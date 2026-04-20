{ ... }:
{
  imports = [
    ../../profiles/worker.nix
    ../../profiles/utility.nix
    ../../profiles/gpu-nvidia.nix
    ../../modules/k3s/node-labels.nix
    ./disko.nix
  ];

  networking.hostName = "enschede-t1000-1";
  personalStack.k3sNodeLabels = {
    "personal-stack/site" = "enschede";
    "personal-stack/node" = "enschede-t1000-1";
    "topology.kubernetes.io/region" = "enschede";
    "personal-stack/role-k3s-worker" = "true";
    "personal-stack/role-utility-host" = "true";
    "personal-stack/capability-tailscale" = "true";
    "personal-stack/capability-lan-ingress" = "true";
    "personal-stack/capability-samba" = "true";
    "personal-stack/capability-adguard" = "true";
    "personal-stack/capability-nvidia" = "true";
    "personal-stack/gpu-vendor-nvidia" = "true";
    "personal-stack/gpu-model-t1000" = "true";
    "personal-stack/gpu-class-transcode" = "true";
  };
  networking.firewall.allowedTCPPorts = [
    8096
    7878
    8989
    # AdGuard Home web UI / API, reachable from the Frankfurt traefik
    # over tailscale0 and from the LAN directly.
    3000
  ];
  system.stateVersion = "25.05";
}
