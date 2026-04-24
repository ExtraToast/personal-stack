{ ... }:
{
  imports = [
    # Second M.2 — dedicated btrfs volume for backups. Lives in its own file
    # so the flake can expose it as diskoConfigurations.enschede-t1000-1-backup
    # and format only that disk without touching disk.main.
    ./disko-backup.nix
  ];

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
