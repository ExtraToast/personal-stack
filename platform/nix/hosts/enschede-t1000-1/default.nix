{ ... }:
{
  imports = [
    ../../profiles/worker.nix
    ../../profiles/utility.nix
    ../../profiles/gpu-nvidia.nix
    ../../modules/k3s/node-labels.nix
    ./disko.nix
  ];

  networking.hostName = "enschede-t1000-1";
  personalStack.k3sNodeLabels = {
    "personal-stack/site" = "enschede";
    "personal-stack/node" = "enschede-t1000-1";
    "topology.kubernetes.io/region" = "enschede";
    "personal-stack/role-k3s-worker" = "true";
    "personal-stack/role-utility-host" = "true";
    "personal-stack/capability-tailscale" = "true";
    "personal-stack/capability-lan-ingress" = "true";
    "personal-stack/capability-samba" = "true";
    "personal-stack/capability-adguard" = "true";
    "personal-stack/capability-nvidia" = "true";
    "personal-stack/gpu-vendor-nvidia" = "true";
    "personal-stack/gpu-model-t1000" = "true";
    "personal-stack/gpu-class-transcode" = "true";
  };
  networking.firewall.allowedTCPPorts = [
    8096
    7878
    8989
    # AdGuard Home web UI / API, reachable from the Frankfurt traefik
    # over tailscale0 and from the LAN directly.
    3000
  ];
  # Advertise the Enschede home LAN to the tailnet so any tailnet peer
  # can reach 192.168.0.1 (ASUS router UI) and other LAN-only hosts
  # natively, without us reverse-proxying their UIs. Only t1000 runs
  # this; the other Enschede nodes are on the same LAN so one
  # advertiser is enough. Requires a one-time route approval in the
  # Tailscale admin console after the NixOS redeploy.
  services.tailscale.extraUpFlags = [ "--advertise-routes=192.168.0.0/24" ];

  # Resolver with a strict failover order:
  #
  #   1. 127.0.0.1 — local AdGuard (systemd service or, after the k8s
  #      migration, the pod with hostPort 53). Handles every query
  #      in normal operation so filter lists / rewrites still apply.
  #   2. 1.1.1.1 / 1.0.0.1 — Cloudflare, used ONLY when the entry
  #      above hard-fails (port closed, process down, kernel returns
  #      ICMP port-unreachable). glibc does not skip past an
  #      entry that answers slowly, so a sluggish AdGuard does not
  #      leak queries to Cloudflare.
  #
  # This closes the chicken-and-egg that bit us during the
  # host-native → k8s migration: when the AdGuardHome service
  # stopped during activation, t1000 itself lost resolution and
  # couldn't pull the replacement container image. With this order,
  # image pulls keep working through AdGuard outages of any length.
  networking.nameservers = [
    "127.0.0.1"
    "1.1.1.1"
    "1.0.0.1"
  ];

  system.stateVersion = "25.05";
}
