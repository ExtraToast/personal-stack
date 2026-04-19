{ config, lib, ... }:
let
  labels = config.personalStack.k3sNodeLabels;
  labelFlags = lib.mapAttrsToList (name: value: "--node-label=${name}=${value}") labels;
in
{
  options.personalStack.k3sNodeLabels = lib.mkOption {
    type = lib.types.attrsOf lib.types.str;
    default = { };
    description = "Inventory-derived Kubernetes node labels exposed through k3s extra flags.";
  };

  config = lib.mkIf (config.services.k3s.enable && labels != { }) {
    services.k3s.extraFlags = lib.mkAfter labelFlags;
  };
}
