{ ... }:
{
  imports = [
    ../../profiles/worker.nix
    ../../profiles/utility.nix
    ../../profiles/gpu-nvidia.nix
    ../../modules/k3s/node-labels.nix
    ../../modules/services/media-storage.nix
    ../../modules/services/samba.nix
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
    # AdGuard Home web UI / API. The k8s pod runs with hostNetwork,
    # so it binds :3000 directly on every host interface; this opens
    # it to LAN + tailnet clients.
    3000
    # AdGuard Home DNS (TCP). Previously opened implicitly by the
    # NixOS services.adguardhome module; now that the module import
    # is gone (migrated to the k8s pod), we open it explicitly.
    53
  ];
  networking.firewall.allowedUDPPorts = [
    # AdGuard Home DNS (UDP). Same rationale as :53 TCP above — the
    # k8s hostNetwork pod binds :53 UDP on every host interface and
    # needs the firewall out of the way.
    53
  ];
  # Advertise the Enschede home LAN to the tailnet so any tailnet peer
  # can reach 192.168.0.1 (ASUS router UI) and other LAN-only hosts
  # natively, without us reverse-proxying their UIs. Only t1000 runs
  # this; the other Enschede nodes are on the same LAN so one
  # advertiser is enough. Requires a one-time route approval in the
  # Tailscale admin console after the NixOS redeploy.
  services.tailscale.extraUpFlags = [ "--advertise-routes=192.168.0.0/24" ];

  # Intentionally NOT configuring networking.nameservers here:
  #
  # AdGuard is meant to serve *LAN clients* (devices on 192.168.0.0/24
  # that the router points at 192.168.0.100 for DNS), not t1000's own
  # syscall-level resolves. Routing the node's own queries through
  # 127.0.0.1:53 means every kubelet image pull, nix fetch, and
  # systemd-resolved lookup depends on the AdGuard pod — which creates
  # an undeployable bootstrap when the pod itself is being (re)started
  # by a NixOS activation.
  #
  # t1000 uses its DHCP-supplied upstream resolver for its own traffic;
  # the AdGuard pod binds 0.0.0.0:53 via hostNetwork so LAN clients
  # still reach it at the node's LAN IP unchanged.

  system.stateVersion = "25.05";
}
