{ lib, ... }:
{
  services.k3s = {
    enable = true;
    role = lib.mkDefault "agent";
  };
}
