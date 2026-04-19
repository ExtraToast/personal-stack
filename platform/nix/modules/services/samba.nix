{ ... }:
{
  services.samba = {
    enable = true;
    openFirewall = true;
    settings.global = {
      workgroup = "WORKGROUP";
      "server string" = "personal-stack-home";
      security = "user";
      "map to guest" = "Bad User";
      "server role" = "standalone server";
      "server min protocol" = "SMB2";
      "vfs objects" = "fruit streams_xattr";
      "fruit:metadata" = "stream";
      "fruit:model" = "MacSamba";
      "fruit:posix_rename" = "yes";
      "fruit:veto_appledouble" = "no";
      "fruit:nfs_aces" = "no";
      "fruit:wipe_intentionally_left_blank_rfork" = "yes";
      "fruit:delete_empty_adfiles" = "yes";
    };
    shares.films = {
      path = "/srv/media/Films";
      browseable = "yes";
      "read only" = "yes";
      "guest ok" = "yes";
    };
    shares.series = {
      path = "/srv/media/Series";
      browseable = "yes";
      "read only" = "yes";
      "guest ok" = "yes";
    };
    shares.anime = {
      path = "/srv/media/Anime";
      browseable = "yes";
      "read only" = "yes";
      "guest ok" = "yes";
    };
    shares.media = {
      path = "/srv/media";
      browseable = "yes";
      "read only" = "no";
      "guest ok" = "no";
      "valid users" = "deploy";
      "force user" = "deploy";
      "force group" = "deploy";
      "create mask" = "0664";
      "directory mask" = "0775";
    };
    shares.timemachine = {
      path = "/srv/media/TimeMachine";
      browseable = "yes";
      "read only" = "no";
      "guest ok" = "no";
      "valid users" = "deploy";
      "force user" = "deploy";
      "force group" = "deploy";
      "vfs objects" = "fruit streams_xattr";
      "fruit:time machine" = "yes";
      "fruit:time machine max size" = "300G";
    };
  };
}
