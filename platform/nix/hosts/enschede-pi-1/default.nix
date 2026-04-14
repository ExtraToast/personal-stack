{ ... }:
{
  imports = [
    ../../profiles/worker.nix
    ../../modules/k3s/node-labels.nix
    ./disko.nix
  ];

  networking.hostName = "enschede-pi-1";
  personalStack.k3sNodeLabels = {
    "personal-stack/site" = "enschede";
    "personal-stack/node" = "enschede-pi-1";
    "topology.kubernetes.io/region" = "enschede";
    "personal-stack/role-k3s-worker" = "true";
    "personal-stack/capability-tailscale" = "true";
  };
  system.stateVersion = "25.05";
}
