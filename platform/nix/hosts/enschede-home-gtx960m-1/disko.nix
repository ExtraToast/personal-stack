{ ... }:
{
  fileSystems."/srv/media" = {
    device = "/dev/disk/by-label/media";
    fsType = "ext4";
  };
}
