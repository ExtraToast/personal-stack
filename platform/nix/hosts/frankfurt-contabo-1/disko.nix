{ ... }:
{
  disko.devices = {
    disk.main = {
      type = "disk";
      device = "/dev/sda";
      content = {
        type = "gpt";
        partitions = {
          # GRUB on GPT needs a tiny BIOS boot partition to stage its core.
          # Must be first so BIOS firmware can chain from the PMBR.
          bios = {
            size = "1M";
            type = "EF02";
            priority = 0;
          };
          boot = {
            size = "512M";
            type = "8300";
            content = {
              type = "filesystem";
              format = "ext4";
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
}
