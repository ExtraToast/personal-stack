{ ... }:
let
  writableMediaShare = path: roleUser: {
    inherit path;
    browseable = "yes";
    "read only" = "no";
    "guest ok" = "no";
    "valid users" = "media-root ${roleUser}";
    "force user" = "deploy";
    "force group" = "deploy";
    "create mask" = "0664";
    "directory mask" = "0775";
  };

  adminMediaShare = path: {
    inherit path;
    browseable = "yes";
    "read only" = "no";
    "guest ok" = "no";
    "valid users" = "media-root";
    "force user" = "deploy";
    "force group" = "deploy";
    "create mask" = "0664";
    "directory mask" = "0775";
  };

  readonlyMediaShare = path: roleUser: {
    inherit path;
    browseable = "yes";
    "read only" = "yes";
    "guest ok" = "no";
    "valid users" = "media-root ${roleUser}";
    "force user" = "deploy";
    "force group" = "deploy";
  };
in
{
  users.groups."media-share" = { };
  users.users = {
    "media-root" = {
      isSystemUser = true;
      group = "media-share";
      description = "All-access Samba identity for the DataBeast media volume";
    };
    "media-downloads" = {
      isSystemUser = true;
      group = "media-share";
      description = "Samba identity for download staging media access";
    };
    "media-tv" = {
      isSystemUser = true;
      group = "media-share";
      description = "Samba identity for TV import media access";
    };
    "media-movies" = {
      isSystemUser = true;
      group = "media-share";
      description = "Samba identity for movie import media access";
    };
    "media-library" = {
      isSystemUser = true;
      group = "media-share";
      description = "Read-only Samba identity for the served media library";
    };
  };

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
    shares."media-admin" = adminMediaShare "/srv/media";
    shares."media-downloads" = writableMediaShare "/srv/media-views/media-downloads" "media-downloads";
    shares."media-tv" = writableMediaShare "/srv/media-views/media-tv" "media-tv";
    shares."media-movies" = writableMediaShare "/srv/media-views/media-movies" "media-movies";
    shares."media-library" = readonlyMediaShare "/srv/media-views/media-library" "media-library";
    shares.timemachine = {
      path = "/srv/media/TimeMachine";
      browseable = "yes";
      "read only" = "no";
      "guest ok" = "no";
      "valid users" = "media-root";
      "force user" = "deploy";
      "force group" = "deploy";
      "vfs objects" = "fruit streams_xattr";
      "fruit:time machine" = "yes";
      "fruit:time machine max size" = "300G";
    };
  };
}
