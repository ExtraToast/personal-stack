{ lib, ... }:
{
  imports = [
    ../modules/roles/gpu-nvidia.nix
  ];

  # NVIDIA driver packages are unfree; keep the allowance scoped to hosts that
  # intentionally opt into the NVIDIA profile.
  nixpkgs.config.allowUnfreePredicate =
    pkg:
    let
      name = lib.getName pkg;
    in
    lib.hasPrefix "nvidia-" name;
}
