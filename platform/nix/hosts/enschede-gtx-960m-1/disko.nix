{ ... }:
{
  fileSystems."/srv/media" = {
    device = "/dev/disk/by-label/media";
    fsType = "ntfs3";
    options = [
      "defaults"
      "noatime"
      "nofail"
      "uid=1000"
      "gid=1000"
      "dmask=0002"
      "fmask=0113"
    ];
  };
}
