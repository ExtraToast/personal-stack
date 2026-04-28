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

  # Write /etc/resolv.conf statically from NixOS and disable openresolv
  # entirely. The earlier `networking.nameservers` + `nohook resolv.conf`
  # approach leaked: the dhcpcd hook that feeds resolv.conf through
  # openresolv is `50-resolvconf`, not `resolv.conf`, so nohook disabled
  # the wrong entry and ISP-supplied resolvers kept getting merged in
  # on top of the three we set. kubelet then parses the >3-entry file
  # and fires DNSConfigForming ("applied nameserver line is: 1.1.1.1
  # 1.0.0.1 9.9.9.9") across every pod that inherits host resolv.conf
  # via `dnsPolicy: Default` or `hostNetwork: true`.
  #
  # With resolvconf disabled and the file written straight from the
  # Nix store, dhcpcd / systemd-networkd / anything else can't mutate
  # /etc/resolv.conf out from under us. Three upstreams: Cloudflare
  # primary + secondary for speed, Quad9 for operator diversity against
  # a Cloudflare-wide outage. IPv6 resolvers are deliberately omitted —
  # AAAA records still resolve over v4 transport and trimming to three
  # leaves no room for glibc's MAXNS=3 truncation to fire.
  #
  # Still deliberately NOT pointing at AdGuard (127.0.0.1 or
  # 192.168.0.100) — AdGuard is itself a k8s pod on t1000, and hosts
  # resolving through it creates an undeployable bootstrap loop during
  # NixOS activations. AdGuard continues to serve LAN clients via the
  # router's DHCP hand-out.
  networking.resolvconf.enable = false;
  environment.etc."resolv.conf" = {
    mode = "0644";
    text = ''
      nameserver 1.1.1.1
      nameserver 1.0.0.1
      nameserver 9.9.9.9
      options timeout:1 attempts:2 rotate
    '';
  };

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

  users.groups.deploy.gid = 1000;
  users.users.deploy = {
    isNormalUser = true;
    uid = 1000;
    group = "deploy";
    extraGroups = [ "wheel" ];
    openssh.authorizedKeys.keys = deployAuthorizedKeys;
  };
  security.sudo.wheelNeedsPassword = false;
  time.timeZone = "Europe/Amsterdam";
  i18n.defaultLocale = "en_US.UTF-8";
}
