{ ... }:
{
  imports = [
    ../../profiles/worker.nix
    ../../profiles/utility.nix
    ../../modules/k3s/node-labels.nix
    ./disko.nix
  ];

  networking.hostName = "enschede-gtx-960m-1";

  # No driver binds the GTX 960M on this host. Runtime D3 needs Turing or
  # newer, so on Maxwell the proprietary driver reports
  # "Runtime D3 status: Disabled by default" and never suspends the GPU:
  # runtime_suspended_time stayed at 0 ms across 29.5 days of uptime while
  # the card idled at P8 with no compute clients. Leaving the driver
  # unloaded is the only way this generation stops drawing idle power, so
  # transcode, CDI and game streaming move to hosts that can use a GPU.
  boot.blacklistedKernelModules = [
    "nouveau"
    "nvidia"
    "nvidia_drm"
    "nvidia_modeset"
    "nvidia_uvm"
  ];
  # With no driver attached the PCI core still needs runtime PM enabled
  # before it will drop the unbound display controller out of D0.
  services.udev.extraRules = ''
    ACTION=="add", SUBSYSTEM=="pci", ATTR{vendor}=="0x10de", ATTR{class}=="0x030000", ATTR{power/control}="auto"
  '';
  personalStack.k3sNodeLabels = {
    "personal-stack/site" = "enschede";
    "personal-stack/node" = "enschede-gtx-960m-1";
    "topology.kubernetes.io/region" = "enschede";
    "personal-stack/role-k3s-worker" = "true";
    "personal-stack/role-utility-host" = "true";
    "personal-stack/capability-tailscale" = "true";
    "personal-stack/capability-lan-ingress" = "true";
    "personal-stack/capability-docker-socket" = "true";
    # capability-samba deliberately absent: the media drive (/srv/media)
    # is mounted on enschede-t1000-1, not here.
    # capability-adguard deliberately absent: AdGuard runs only on
    # enschede-t1000-1.
  };
  # Keep Testcontainers' status.hostIP callback stable and aligned with the
  # runner NetworkPolicy's single allowed node IP.
  services.k3s.extraFlags = [ "--node-ip=100.89.41.92" ];
  # Testcontainers maps container ports onto random high ports on the host
  # Docker daemon. Runner Pods reach those through status.hostIP, so allow
  # callbacks from the pod bridge only; do not expose the range on LAN/tailnet.
  networking.firewall.interfaces."cni0".allowedTCPPortRanges = [
    {
      from = 32768;
      to = 65535;
    }
  ];
  # Docker's socket group must be a repo-declared value, not a guessed
  # distro default. assistant-api injects the same gid into runner Pods via
  # AGENT_RUNTIME_DOCKER_SOCKET_SUPPLEMENTAL_GROUPS.
  users.groups.docker.gid = 131;
  system.stateVersion = "25.05";
}
