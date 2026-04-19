{ imageBuild ? false, lib, ... }:
{
  imports =
    [
      ../../profiles/worker.nix
      ../../modules/k3s/node-labels.nix
      ../../modules/hardware/raspberry-pi-aarch64.nix
    ]
    ++ lib.optional (!imageBuild) ./disko.nix;

  # Raspberry Pi images boot through firmware + U-Boot/extlinux rather than EFI/systemd-boot.
  boot.loader = {
    grub.enable = lib.mkForce false;
    systemd-boot.enable = lib.mkForce false;
    efi.canTouchEfiVariables = lib.mkForce false;
    generic-extlinux-compatible.enable = true;
  };

  networking.hostName = "enschede-pi-2";
  personalStack.k3sNodeLabels = {
    "personal-stack/site" = "enschede";
    "personal-stack/node" = "enschede-pi-2";
    "topology.kubernetes.io/region" = "enschede";
    "personal-stack/role-k3s-worker" = "true";
    "personal-stack/capability-tailscale" = "true";
  };
  system.stateVersion = "25.05";
}
