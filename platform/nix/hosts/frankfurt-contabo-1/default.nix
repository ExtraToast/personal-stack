{ lib, modulesPath, ... }:
{
  imports = [
    # Provides virtio_scsi / virtio_blk / virtio_net in the initrd plus the
    # qemu-guest agent. Without this the initrd cannot mount /dev/sda on
    # Contabo's virtio-scsi host, the kernel panics on "unable to mount root"
    # before serial console or journald are up, and the machine appears alive
    # to the hypervisor but never comes back on the network.
    (modulesPath + "/profiles/qemu-guest.nix")
    ../../profiles/worker.nix
    ../../profiles/control-plane.nix
    ../../modules/k3s/node-labels.nix
    ./disko.nix
  ];

  # Contabo's KVM firmware is BIOS-only (no /sys/firmware/efi in rescue),
  # so the base module's systemd-boot can't be executed by the firmware.
  # Use GRUB in BIOS mode against the MBR disko layout.
  boot.loader = {
    systemd-boot.enable = lib.mkForce false;
    efi.canTouchEfiVariables = lib.mkForce false;
    timeout = 1;
    # grub.devices is injected by disko from disk.main.device ("/dev/sda"),
    # so we don't set it here — doing so duplicates the entry and trips
    # the `mirroredBoots` assertion.
    grub = {
      enable = true;
      efiSupport = false;
      # Copy kernels into /boot instead of symlinking into /nix/store so
      # GRUB stage-2 can load them without understanding the full store path.
      copyKernels = true;
      # Proceed even when grub-install's platform probe is uncertain — BIOS
      # hosts sometimes trip on GPT remnants from previous installs.
      forceInstall = true;
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
