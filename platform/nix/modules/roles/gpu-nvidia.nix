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
  # With `pkgs.nvidia-container-toolkit.tools` on k3s's systemd PATH,
  # k3s's built-in nvidia detection pass runs at startup, sees
  # `nvidia-container-runtime` on PATH, and appends the correct
  # containerd runtime blocks for both `nvidia` (legacy hook wrapper)
  # and `nvidia-cdi` (CDI-based injection). Those live under the v2
  # `plugins.\"io.containerd.grpc.v1.cri\"...` namespace that kubelet
  # talks to, with `SystemdCgroup = true` to match k3s's default runc
  # handler.
  #
  # We intentionally do NOT emit a containerd config template of our
  # own. An earlier iteration added a duplicate `nvidia` block under
  # the v3 `plugins.'io.containerd.cri.v1.runtime'` namespace; that
  # collided with k3s's v2 entry and pods scheduled under
  # `runtimeClassName: nvidia` failed with runc cgroupsPath errors
  # because the v3 block wasn't participating in the v2 OCI-spec
  # generator pipeline kubelet uses.
  #
  # `pkgs.nvidia-container-toolkit` is the default output and contains
  # only `nvidia-ctk`. The OCI runtime binary itself lives in a
  # separate derivation, `pkgs.nvidia-container-toolkit.tools` (shows
  # up in /nix/store as `...-nvidia-container-toolkit-<ver>-tools/`);
  # that's the one k3s needs on PATH.
  # `nvidia-container-runtime` is a thin wrapper that injects the NVIDIA
  # pre-start hook into the OCI spec and then re-execs a low-level
  # runtime (runc or crun) looked up on PATH. k3s ships its own runc but
  # calls it by absolute path internally, so nothing exposes `runc` on
  # PATH for child processes. Without this the wrapper crashes the
  # instant containerd invokes it, with
  #   error locating runtime: no runtime binary found from
  #     candidate list: [runc crun]
  # and the kubelet event surfaces the opaque
  #   .../nvidia-container-runtime did not terminate successfully:
  #     exit status 2
  # Put `pkgs.runc` on the k3s service PATH alongside the toolkit so the
  # wrapper can resolve its low-level runtime.
  systemd.services.k3s.path = lib.mkIf config.services.k3s.enable [
    pkgs.nvidia-container-toolkit.tools
    pkgs.runc
  ];
}
