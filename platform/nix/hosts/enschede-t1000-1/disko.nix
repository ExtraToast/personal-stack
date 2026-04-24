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
      # Pinned by model+serial because t1000 now has two NVMe drives
      # (Samsung 980 = root, MZVLB = backup) and /dev/nvmeXnY enumeration
      # is not guaranteed stable across reboots — a recent reboot already
      # swapped their order. A `disko --mode format` targeting a bare
      # /dev/nvme0n1 would wipe whichever drive happened to enumerate
      # first; the by-id path always resolves to the root SSD.
      device = "/dev/disk/by-id/nvme-Samsung_SSD_980_500GB_S64DNX0T357117Y";
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
