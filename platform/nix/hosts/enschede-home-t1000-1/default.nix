{ ... }:
{
  imports = [
    ../../profiles/worker.nix
    ../../profiles/utility.nix
    ../../profiles/gpu-nvidia.nix
    ./disko.nix
  ];

  networking.hostName = "enschede-home-t1000-1";
  networking.firewall.allowedTCPPorts = [
    8096
    7878
    8989
  ];
  system.stateVersion = "25.05";
}
