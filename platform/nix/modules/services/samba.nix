{ ... }:
{
  services.samba = {
    enable = true;
    openFirewall = true;
    settings.global = {
      "map to guest" = "Bad User";
      "server min protocol" = "SMB2";
    };
    shares.media = {
      path = "/srv/media";
      browseable = "yes";
      "read only" = "no";
      "guest ok" = "no";
    };
  };
}
