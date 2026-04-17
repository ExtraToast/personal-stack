{ lib, ... }:
{
  # Runtime hardware bits for aarch64 Raspberry Pi workers. These must apply to
  # both the SD image build and steady-state nixos-rebuild switch; otherwise the
  # post-deploy initrd loses the SD/eMMC driver and the kernel hangs silently
  # right after "Starting kernel" with no console.
  hardware.enableRedistributableFirmware = true;
  hardware.deviceTree.enable = true;

  boot.initrd.availableKernelModules = [
    "mmc_block"
    "sdhci_iproc"
    "dwc2"
    "pcie_brcmstb"
    "usbhid"
    "usb_storage"
  ];

  boot.kernelParams = [
    "console=tty1"
    "console=ttyS0,115200n8"
    "console=ttyAMA0,115200n8"
  ];

  boot.consoleLogLevel = lib.mkDefault 7;
}
