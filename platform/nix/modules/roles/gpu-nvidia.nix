{ config, lib, pkgs, ... }:
{
  services.xserver.videoDrivers = [ "nvidia" ];
  hardware.graphics.enable = true;
  hardware.nvidia = {
    open = false;
    modesetting.enable = true;
    package = config.boot.kernelPackages.nvidiaPackages.stable;
  };
  hardware.nvidia-container-toolkit.enable = true;

  environment.systemPackages = with pkgs; [
    libva
    pciutils
  ];

  # GPU + k3s: make containerd aware of the `nvidia` OCI runtime.
  #
  # `hardware.nvidia-container-toolkit.enable = true` only generates the
  # CDI spec at /var/run/cdi/nvidia-container-toolkit.json; it does NOT
  # put nvidia-container-runtime on any service's PATH, and k3s's
  # auto-detection pass scans the k3s systemd unit's PATH at startup, so
  # without both halves below the `nvidia` RuntimeClass remains
  # unbacked -- pods using it fail sandbox creation with
  #   "no runtime for \"nvidia\" is configured".
  #
  # Two things are needed on a k3s node with a GPU:
  #   1. `nvidia-container-runtime` reachable on the k3s service PATH so
  #      k3s's containerd can exec it.
  #   2. A containerd config template that registers the `nvidia`
  #      runtime handler under the CRI runtimes block so containerd
  #      routes pods with runtimeClassName: nvidia through it.
  #
  # Guard both behind `services.k3s.enable` so this profile stays
  # useful on non-k3s GPU hosts (e.g. a future workstation import).
  systemd.services.k3s.path = lib.mkIf config.services.k3s.enable [
    pkgs.nvidia-container-toolkit
  ];

  services.k3s.containerdConfigTemplate = lib.mkIf config.services.k3s.enable ''
    {{ template "base" . }}

    [plugins.'io.containerd.cri.v1.runtime'.containerd.runtimes.nvidia]
      runtime_type = "io.containerd.runc.v2"

    [plugins.'io.containerd.cri.v1.runtime'.containerd.runtimes.nvidia.options]
      BinaryName = "${pkgs.nvidia-container-toolkit}/bin/nvidia-container-runtime"
      SystemdCgroup = true
  '';
}
