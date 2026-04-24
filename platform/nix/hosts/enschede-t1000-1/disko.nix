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

    # Second M.2 — dedicated btrfs volume for backups of the external HDD and
    # other important files. Pinned by model+serial because the host has two
    # NVMes and /dev/nvmeXnY enumeration is not guaranteed stable across boots.
    disk.backup = {
      type = "disk";
      device = "/dev/disk/by-id/nvme-SAMSUNG_MZVLB512HBJQ-000L7_S4ENNF0M777358";
      content = {
        type = "gpt";
        partitions.backup = {
          size = "100%";
          content = {
            type = "btrfs";
            extraArgs = [ "-L" "backup" "-f" ];
            subvolumes = {
              "@backup" = {
                mountpoint = "/srv/backup";
                mountOptions = [ "compress=zstd:3" "noatime" ];
              };
              "@snapshots" = {
                mountpoint = "/srv/backup/.snapshots";
                mountOptions = [ "compress=zstd:3" "noatime" ];
              };
            };
          };
        };
      };
    };
  };

  fileSystems."/srv/media" = {
    # The 6 TB NTFS drive ships from the old home node labeled "DataBeast".
    # If you relabel it (sudo ntfslabel /dev/sdX <new>) make sure to update
    # this line to match.
    device = "/dev/disk/by-label/DataBeast";
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
