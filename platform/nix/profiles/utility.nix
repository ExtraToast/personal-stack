{ ... }:
{
  imports = [
    ../modules/roles/utility-host.nix
    ../modules/services/adguard.nix
    ../modules/services/media-storage.nix
    ../modules/services/samba.nix
  ];
}
