{ lib, ... }:
{
  imports = [
    ../modules/roles/gpu-amd.nix
  ];

  # ROCm components and AMDVLK are redistributable-firmware /
  # permissive but a few sub-packages are gated as unfree. Keep the
  # allowance scoped to hosts that opt into the AMD profile, mirroring
  # how profiles/gpu-nvidia.nix narrows the NVIDIA allowlist.
  nixpkgs.config.allowUnfreePredicate =
    pkg:
    let
      name = lib.getName pkg;
    in
    lib.hasPrefix "amdgpu-" name
    || lib.hasPrefix "amdvlk" name
    || lib.hasPrefix "rocm" name
    || lib.hasPrefix "hip" name
    || lib.hasPrefix "libretro-" name;
}
