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
