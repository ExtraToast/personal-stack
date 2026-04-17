{ config, lib, modulesPath, ... }:
{
  imports = [ (modulesPath + "/installer/sd-card/sd-image-aarch64.nix") ];

  # Build a first-boot-ready SD card image from the real host definition instead
  # of treating the generic Pi installer image as a remote install target.
  image.baseName = lib.mkDefault config.networking.hostName;
}
