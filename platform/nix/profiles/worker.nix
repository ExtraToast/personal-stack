{ ... }:
{
  imports = [
    ../modules/base/default.nix
    ../modules/roles/worker.nix
    ../modules/services/tailscale.nix
  ];
}
