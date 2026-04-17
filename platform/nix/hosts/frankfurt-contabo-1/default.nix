{ lib, ... }:
{
  imports = [
    ../../profiles/worker.nix
    ../../profiles/control-plane.nix
    ../../modules/k3s/node-labels.nix
    ./disko.nix
  ];

  # Contabo's KVM firmware is BIOS-only (no /sys/firmware/efi in rescue),
  # so the base module's systemd-boot can't be executed by the firmware.
  # Switch this host to GRUB in BIOS mode and rely on the bios_grub
  # partition defined in disko.nix.
  boot.loader = {
    systemd-boot.enable = lib.mkForce false;
    efi.canTouchEfiVariables = lib.mkForce false;
    # grub.devices is injected by disko from disk.main.device ("/dev/sda"),
    # so we don't set it here — doing so duplicates the entry and trips
    # the `mirroredBoots` assertion.
    grub = {
      enable = true;
      efiSupport = false;
    };
  };

  networking.hostName = "frankfurt-contabo-1";
  personalStack.k3sNodeLabels = {
    "personal-stack/site" = "frankfurt";
    "personal-stack/node" = "frankfurt-contabo-1";
    "topology.kubernetes.io/region" = "frankfurt";
    "personal-stack/role-k3s-control-plane" = "true";
    "personal-stack/role-k3s-worker" = "true";
    "personal-stack/capability-tailscale" = "true";
    "personal-stack/capability-public-ingress" = "true";
  };
  system.stateVersion = "25.05";
}
