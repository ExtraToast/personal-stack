{ config, lib, ... }:
let
  labels = config.personalStack.k3sNodeLabels;
  labelFlags = lib.mapAttrsToList (name: value: "--node-label=${name}=${value}") labels;
  taints = config.personalStack.k3sNodeTaints;
  taintFlags = map (taint: "--node-taint=${taint}") taints;
in
{
  options.personalStack.k3sNodeLabels = lib.mkOption {
    type = lib.types.attrsOf lib.types.str;
    default = { };
    description = "Inventory-derived Kubernetes node labels exposed through k3s extra flags.";
  };

  options.personalStack.k3sNodeTaints = lib.mkOption {
    type = lib.types.listOf lib.types.str;
    default = [ ];
    example = [ "personal-stack/intermittent=true:NoSchedule" ];
    description = "Kubernetes node taints (key=value:Effect) exposed through k3s extra flags.";
  };

  config = lib.mkIf (config.services.k3s.enable && (labels != { } || taints != [ ])) {
    services.k3s.extraFlags = lib.mkAfter (labelFlags ++ taintFlags);
  };
}
