{ ... }:
{
  imports = [
    ../../profiles/worker.nix
    ./disko.nix
  ];

  networking.hostName = "enschede-pi-2";
  system.stateVersion = "25.05";
}
