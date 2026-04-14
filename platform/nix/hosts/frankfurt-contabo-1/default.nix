{ ... }:
{
  imports = [
    ../../profiles/control-plane.nix
    ./disko.nix
  ];

  networking.hostName = "frankfurt-contabo-1";
  system.stateVersion = "25.05";
}
