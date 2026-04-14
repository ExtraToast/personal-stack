{ ... }:
{
  imports = [
    ../modules/base/default.nix
    ../modules/roles/control-plane.nix
    ../modules/services/tailscale.nix
  ];
}
