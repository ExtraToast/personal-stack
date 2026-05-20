{ config, lib, pkgs, ... }:
let
  # rocm-smi is a Python script that calls ctypes.CDLL("libdrm_amdgpu.so")
  # with a bare soname. NixOS has no ld.so.cache and the system PATH
  # does not influence dlopen() lookups, so even with pkgs.libdrm present
  # on disk the call fails with "Fail to open libdrm_amdgpu.so" and the
  # tool reports "get_name, Failed to load a library" / Card Series N/A.
  # Wrap the upstream binary so LD_LIBRARY_PATH points at pkgs.libdrm
  # for this one invocation. Wolf, Ollama, RADV, and rocminfo all bind
  # against Mesa or HSA and are unaffected.
  rocm-smi-wrapped = pkgs.symlinkJoin {
    name = "rocm-smi-wrapped";
    paths = [ pkgs.rocmPackages.rocm-smi ];
    nativeBuildInputs = [ pkgs.makeWrapper ];
    postBuild = ''
      wrapProgram $out/bin/rocm-smi \
        --prefix LD_LIBRARY_PATH : ${pkgs.libdrm}/lib
    '';
  };
in
{
  # AMD modern desktop GPUs (RDNA1+, including RDNA3 / Navi 31 on the
  # RX 7900 XTX) use the in-tree amdgpu kernel module — no out-of-tree
  # driver. The userspace stack is Mesa (RADV/RadeonSI) for Vulkan/GL
  # and VAAPI; ROCm provides the compute runtime for Ollama / PyTorch /
  # llama.cpp.
  boot.initrd.kernelModules = [ "amdgpu" ];
  boot.kernelModules = [ "amdgpu" "kvm-amd" ];

  # Make sure firmware blobs (radeon/amdgpu microcode) are present —
  # they ship in linux-firmware which is unfree-redistributable.
  hardware.enableRedistributableFirmware = true;

  # RADV (Mesa's Vulkan ICD) is the default Vulkan path for AMD on
  # modern nixpkgs; AMD's proprietary amdvlk package was deprecated
  # and removed upstream. RDNA3 is RADV-supported end to end, so no
  # second Vulkan ICD is needed. libvdpau-va-gl bridges VDPAU
  # consumers to VAAPI on the AMD path.
  hardware.graphics = {
    enable = true;
    enable32Bit = true;
    extraPackages = with pkgs; [
      libva
      libvdpau-va-gl
      mesa
      rocmPackages.clr.icd
    ];
    extraPackages32 = with pkgs.pkgsi686Linux; [
      mesa
    ];
  };

  # ROCm runtime + tooling for compute. `clr` provides HIP/OpenCL,
  # rocminfo covers HSA diagnostics. PyTorch / llama.cpp / Ollama all
  # bind against these at runtime via the /opt/rocm symlink baked by
  # the userspace. rocm-smi is replaced by rocm-smi-wrapped above so
  # its libdrm_amdgpu.so dlopen succeeds — keep it out of this list
  # so the wrapped binary wins the PATH race.
  environment.systemPackages = with pkgs; [
    clinfo
    libdrm
    libva-utils
    pciutils
    radeontop
    rocm-smi-wrapped
    rocmPackages.rocminfo
    vulkan-tools
  ];

  # Containers (Wolf, ROCm Ollama, anything via k3s) need /dev/dri
  # and /dev/kfd accessible to non-root callers. The render group
  # owns /dev/dri/renderD*; kfd is created with mode 0666 by amdgpu
  # but we still tag it for predictability.
  services.udev.extraRules = ''
    KERNEL=="kfd", GROUP="render", MODE="0660"
  '';

  # Expose ROCm devices to OCI runtimes. There is no NVIDIA-style CDI
  # generator for AMD; the standard pattern is bind-mounting /dev/dri
  # + /dev/kfd into containers (Wolf does this) and, for k8s, the
  # ROCm device plugin DaemonSet which advertises `amd.com/gpu`. The
  # device plugin is deployed cluster-side, not from this module.
  systemd.tmpfiles.rules = [
    "d /var/lib/personal-stack/rocm 0755 root render - -"
  ];

  # Mainline amdgpu sometimes needs the firmware path explicit on
  # kernels older than the firmware blob it expects. Harmless on
  # current kernels; keeps reinstall on an older ISO reproducible.
  hardware.firmware = [ pkgs.linux-firmware ];

  # k3s + AMD GPU containers: containerd's default runc handler is
  # sufficient — there is no NVIDIA-style alternate runtime — but
  # pods that want the GPU need /dev/kfd and /dev/dri bound in via
  # the ROCm device plugin. No containerd config template required.
  assertions = [
    {
      assertion = config.hardware.graphics.enable;
      message = "gpu-amd role requires hardware.graphics.enable for Mesa userspace.";
    }
  ];
}
