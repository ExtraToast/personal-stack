{ ... }:
{
  imports = [
    ../../profiles/worker.nix
    ./disko.nix
  ];

  networking.hostName = "enschede-pi-1";
  system.stateVersion = "25.05";
}
