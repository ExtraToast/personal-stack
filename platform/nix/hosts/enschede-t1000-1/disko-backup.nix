{ ... }:
{
  # Extracted so the flake can expose it as `diskoConfigurations.enschede-t1000-1-backup`
  # and run `disko --mode format` against ONLY this disk. disko's CLI has no
  # --disk scoping flag, so formatting the full host config would also wipe
  # disk.main (nvme0n1, the live root).
  disko.devices.disk.backup = {
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
}
