{ ... }:
{
  imports = [
    ../modules/roles/utility-host.nix
    # AdGuard intentionally NOT imported here. It binds 0.0.0.0:3000
    # and we only want a single LAN-wide DNS resolver, so the service
    # is enabled explicitly on exactly one host (enschede-t1000-1) via
    # its host-level default.nix.
    # media-storage and samba intentionally NOT imported here: the
    # /srv/media drive and Samba service live only on enschede-t1000-1.
  ];
}
