{ ... }:
{
  imports = [
    ../modules/base/default.nix
    ../modules/roles/utility-host.nix
    ../modules/services/tailscale.nix
    ../modules/services/adguard.nix
    ../modules/services/samba.nix
  ];
}
