{ pkgs, ... }:
let
  authorizedKeysPath = ../../authorized-keys.nix;
  sharedAuthorizedKeys =
    if builtins.pathExists authorizedKeysPath then
      import authorizedKeysPath
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

  users.users.deploy = {
    isNormalUser = true;
    extraGroups = [ "wheel" ];
    openssh.authorizedKeys.keys = sharedAuthorizedKeys;
  };
  security.sudo.wheelNeedsPassword = false;
  time.timeZone = "Europe/Amsterdam";
  i18n.defaultLocale = "en_US.UTF-8";
}
