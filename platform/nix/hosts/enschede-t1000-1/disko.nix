{ ... }:
{
  disko.devices = {
    disk.main = {
      type = "disk";
      device = "/dev/nvme0n1";
      content = {
        type = "gpt";
        partitions = {
          ESP = {
            size = "1G";
            type = "EF00";
            content = {
              type = "filesystem";
              format = "vfat";
              mountpoint = "/boot";
            };
          };
          root = {
            size = "100%";
            content = {
              type = "filesystem";
              format = "ext4";
              mountpoint = "/";
            };
          };
        };
      };
    };
  };

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
