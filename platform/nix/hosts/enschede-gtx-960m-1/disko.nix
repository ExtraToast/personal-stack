{ lib, ... }:
{
  # TODO: replace these placeholders with a real disko block once this host
  # is ready for NixOS install. Until then these stubs let `nix flake check`
  # evaluate the host — they're tagged `mkDefault` so the install-time disko
  # layout can override them without conflict. The NTFS media disk is no
  # longer mounted here; it now lives on enschede-t1000-1 (see
  # platform/nix/hosts/enschede-t1000-1/disko.nix).
  fileSystems."/" = lib.mkDefault {
    device = "/dev/disk/by-label/nixos";
    fsType = "ext4";
  };
  fileSystems."/boot" = lib.mkDefault {
    device = "/dev/disk/by-label/BOOT";
    fsType = "vfat";
  };
}
