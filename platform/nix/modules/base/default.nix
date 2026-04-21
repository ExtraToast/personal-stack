{ config, lib, pkgs, ... }:
let
  authorizedKeysDir = ../../authorized-keys;
  deployAuthorizedKeysPath = authorizedKeysDir + "/deploy.pub";
  deployAuthorizedKeys =
    if builtins.pathExists deployAuthorizedKeysPath then
      lib.filter (line: line != "" && !(lib.hasPrefix "#" line)) (
        lib.splitString "\n" (builtins.readFile deployAuthorizedKeysPath)
      )
    else
      [ ];
in
{
  boot.loader.systemd-boot.enable = true;
  boot.loader.efi.canTouchEfiVariables = true;

  networking.useDHCP = true;
  networking.firewall.enable = true;
  networking.firewall.allowedTCPPorts = [ 2222 ];

  # Fleet-wide resolver. Pins all nodes to Cloudflare + Quad9 so CoreDNS
  # (which forwards to /etc/resolv.conf on whichever host it runs on) gets
  # a fast, consistent upstream regardless of the node's DHCP lease.
  # `nohook resolv.conf` stops dhcpcd from re-appending the ISP-supplied
  # resolver on lease renewal; without it the list grows silently and
  # glibc falls back to slow upstreams under load. `rotate` spreads query
  # load across the list; `timeout:1 attempts:2` caps worst-case DNS wait
  # at ~2s to keep bursty HTTP clients (Jellyfin HomeScreenSections) from
  # exhausting their HttpClient pool on a single slow resolver.
  #
  # Capped at three entries because glibc's MAXNS is 3 — Kubernetes emits
  # a DNSConfigForming warning and silently drops the extras on every pod
  # that inherits resolv.conf via dnsPolicy Default / hostNetwork. Two
  # Cloudflare IPs plus Quad9 covers the primary+secondary Cloudflare
  # pair *and* keeps one non-Cloudflare operator for the rare case of a
  # Cloudflare-wide outage. Dual-stack v6 resolvers are skipped: pods
  # can still resolve AAAA records over v4 transport, so there's no loss.
  #
  # Deliberately NOT pointing at AdGuard (127.0.0.1 or 192.168.0.100) —
  # AdGuard is itself a k8s pod on t1000, and hosts resolving through it
  # creates an undeployable bootstrap loop during NixOS activations.
  # AdGuard continues to serve LAN clients via the router's DHCP hand-out.
  networking.nameservers = [
    "1.1.1.1"
    "1.0.0.1"
    "9.9.9.9"
  ];
  networking.dhcpcd.extraConfig = "nohook resolv.conf";
  networking.resolvconf.extraOptions = [ "timeout:1" "attempts:2" "rotate" ];

  services.openssh = {
    enable = true;
    ports = [ 2222 ];
    settings = {
      AllowUsers = [ "deploy" ];
      KbdInteractiveAuthentication = false;
      PasswordAuthentication = false;
      PermitRootLogin = "no";
      PubkeyAuthentication = true;
      X11Forwarding = false;
    };
  };

  environment.systemPackages = with pkgs; [
    curl
    git
    jq
    vim
  ];

  # A missing deploy.pub is caught loudly at install/deploy time by the bash
  # guards in platform/scripts. Surface it here as a warning too, but do not
  # fail the build — otherwise `nix flake check` blows up on a clean
  # checkout (deploy.pub is gitignored per design).
  warnings = lib.optional (deployAuthorizedKeys == [ ]) ''
    No deploy SSH public keys configured in ${toString deployAuthorizedKeysPath}.
    The `deploy` user on this host will have no authorized keys. Create the
    file locally (see platform/nix/authorized-keys/README.md) before the next
    install or deploy-rs activation.
  '';

  users.users.deploy = {
    isNormalUser = true;
    extraGroups = [ "wheel" ];
    openssh.authorizedKeys.keys = deployAuthorizedKeys;
  };
  security.sudo.wheelNeedsPassword = false;
  time.timeZone = "Europe/Amsterdam";
  i18n.defaultLocale = "en_US.UTF-8";
}
