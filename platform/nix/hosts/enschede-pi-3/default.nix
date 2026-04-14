{ ... }:
{
  imports = [
    ../../profiles/worker.nix
    ./disko.nix
  ];

  networking.hostName = "enschede-pi-3";
  system.stateVersion = "25.05";
}
