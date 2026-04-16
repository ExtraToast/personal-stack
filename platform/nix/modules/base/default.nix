{ config, lib, pkgs, ... }:
let
  authorizedKeysDir = ../../authorized-keys;
  hostAuthorizedKeyPath = authorizedKeysDir + "/${config.networking.hostName}.pub";
  hostAuthorizedKeyLines =
    if builtins.pathExists hostAuthorizedKeyPath then
      lib.filter (line: line != "" && !(lib.hasPrefix "#" line)) (
        lib.splitString "\n" (builtins.readFile hostAuthorizedKeyPath)
      )
    else
      [ ];
  hostAuthorizedKey =
    if hostAuthorizedKeyLines == [ ] then
      null
    else
      builtins.head hostAuthorizedKeyLines;
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

  assertions = lib.optional (builtins.pathExists hostAuthorizedKeyPath) {
    assertion = lib.length hostAuthorizedKeyLines == 1;
    message =
      "Expected exactly one deploy SSH public key in ${toString hostAuthorizedKeyPath}; found ${toString (lib.length hostAuthorizedKeyLines)}";
  };

  users.users.deploy = {
    isNormalUser = true;
    extraGroups = [ "wheel" ];
    openssh.authorizedKeys.keys = lib.optional (hostAuthorizedKey != null) hostAuthorizedKey;
  };
  security.sudo.wheelNeedsPassword = false;
  time.timeZone = "Europe/Amsterdam";
  i18n.defaultLocale = "en_US.UTF-8";
}
