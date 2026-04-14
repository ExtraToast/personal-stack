{ ... }:
{
  imports = [
    ../../profiles/worker.nix
    ../../profiles/control-plane.nix
    ../../modules/k3s/node-labels.nix
    ./disko.nix
  ];

  networking.hostName = "frankfurt-contabo-1";
  personalStack.k3sNodeLabels = {
    "personal-stack/site" = "frankfurt";
    "personal-stack/node" = "frankfurt-contabo-1";
    "topology.kubernetes.io/region" = "frankfurt";
    "personal-stack/role-k3s-control-plane" = "true";
    "personal-stack/role-k3s-worker" = "true";
    "personal-stack/capability-tailscale" = "true";
    "personal-stack/capability-public-ingress" = "true";
  };
  system.stateVersion = "25.05";
}
