{ config, lib, pkgs, ... }:
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
  # rocminfo / rocm-smi cover diagnostics. PyTorch / llama.cpp /
  # Ollama all bind against these at runtime via /opt/rocm symlink
  # baked by the userspace. libdrm is pulled in explicitly so
  # rocm-smi's product-name lookup (which dlopens libdrm_amdgpu.so)
  # stops emitting "Fail to open libdrm_amdgpu.so" / "get_name, Failed
  # to load a library" on every invocation. Without it the GPU still
  # reports VRAM and gfx1100 fine, but the card series / model fields
  # come back N/A — purely a diagnostics-quality fix.
  environment.systemPackages = with pkgs; [
    clinfo
    libdrm
    libva-utils
    pciutils
    radeontop
    rocmPackages.rocminfo
    rocmPackages.rocm-smi
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
