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

  assertions = [
    {
      assertion = deployAuthorizedKeys != [ ];
      message =
        "Expected at least one deploy SSH public key in ${toString deployAuthorizedKeysPath}";
    }
  ];

  users.users.deploy = {
    isNormalUser = true;
    extraGroups = [ "wheel" ];
    openssh.authorizedKeys.keys = deployAuthorizedKeys;
  };
  security.sudo.wheelNeedsPassword = false;
  time.timeZone = "Europe/Amsterdam";
  i18n.defaultLocale = "en_US.UTF-8";
}
