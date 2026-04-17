{ lib, ... }:
{
  # Pi nodes are installed by flashing the SD image built from
  # sd-image-aarch64.nix, not by nixos-anywhere, so there is no disko run
  # on the device. Mirror the labels the SD image creates so the
  # steady-state fileSystems match what is actually on the card.
  fileSystems."/" = {
    device = "/dev/disk/by-label/NIXOS_SD";
    fsType = "ext4";
  };
  fileSystems."/boot/firmware" = {
    device = "/dev/disk/by-label/FIRMWARE";
    fsType = "vfat";
    options = [ "nofail" "noauto" ];
  };
}
