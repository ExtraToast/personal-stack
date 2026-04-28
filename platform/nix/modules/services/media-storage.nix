{ pkgs, ... }:
{
  # ntfs3g ships the userspace NTFS repair tools (ntfsfix, ntfslabel,
  # ntfsinfo…). The kernel ntfs3 driver handles the read/write path for
  # /srv/media, but after an unclean shutdown it refuses dirty volumes
  # until the dirty flag is cleared — which is exactly what
  # `ntfsfix -d /dev/disk/by-label/DataBeast` is for. Keep the tools on
  # PATH so the next recovery is a one-liner instead of a nix-build
  # dance.
  environment.systemPackages = [ pkgs.ntfs3g ];

  systemd.tmpfiles.rules = [
    "d /srv/media 0775 deploy deploy - -"
    "d /srv/media/Completed 0775 deploy deploy - -"
    "d /srv/media/Downloading 0775 deploy deploy - -"
    "d /srv/media/Films 0775 deploy deploy - -"
    "d /srv/media/Series 0775 deploy deploy - -"
    "d /srv/media/Anime 0775 deploy deploy - -"
    "d /srv/media/TimeMachine 0775 deploy deploy - -"
    "d /srv/media-views 0755 root root - -"
    "d /srv/media-views/media-downloads 0755 root root - -"
    "d /srv/media-views/media-downloads/Downloading 0755 root root - -"
    "d /srv/media-views/media-downloads/Completed 0755 root root - -"
    "d /srv/media-views/media-series 0755 root root - -"
    "d /srv/media-views/media-series/Completed 0755 root root - -"
    "d /srv/media-views/media-series/Series 0755 root root - -"
    "d /srv/media-views/media-movies 0755 root root - -"
    "d /srv/media-views/media-movies/Completed 0755 root root - -"
    "d /srv/media-views/media-movies/Films 0755 root root - -"
    "d /srv/media-views/media-library 0755 root root - -"
    "d /srv/media-views/media-library/Series 0755 root root - -"
    "d /srv/media-views/media-library/Films 0755 root root - -"
    "d /var/lib/personal-stack 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/qbittorrent 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/prowlarr 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/bazarr 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/sonarr 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/radarr 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/jellyfin 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/jellyseerr 0755 deploy deploy - -"
  ];

  fileSystems = {
    "/srv/media-views/media-downloads/Downloading" = {
      device = "/srv/media/Downloading";
      fsType = "none";
      options = [
        "bind"
        "nofail"
      ];
      depends = [ "/srv/media" ];
    };
    "/srv/media-views/media-downloads/Completed" = {
      device = "/srv/media/Completed";
      fsType = "none";
      options = [
        "bind"
        "nofail"
      ];
      depends = [ "/srv/media" ];
    };
    "/srv/media-views/media-series/Completed" = {
      device = "/srv/media/Completed";
      fsType = "none";
      options = [
        "bind"
        "nofail"
      ];
      depends = [ "/srv/media" ];
    };
    "/srv/media-views/media-series/Series" = {
      device = "/srv/media/Series";
      fsType = "none";
      options = [
        "bind"
        "nofail"
      ];
      depends = [ "/srv/media" ];
    };
    "/srv/media-views/media-movies/Completed" = {
      device = "/srv/media/Completed";
      fsType = "none";
      options = [
        "bind"
        "nofail"
      ];
      depends = [ "/srv/media" ];
    };
    "/srv/media-views/media-movies/Films" = {
      device = "/srv/media/Films";
      fsType = "none";
      options = [
        "bind"
        "nofail"
      ];
      depends = [ "/srv/media" ];
    };
    "/srv/media-views/media-library/Series" = {
      device = "/srv/media/Series";
      fsType = "none";
      options = [
        "bind"
        "nofail"
      ];
      depends = [ "/srv/media" ];
    };
    "/srv/media-views/media-library/Films" = {
      device = "/srv/media/Films";
      fsType = "none";
      options = [
        "bind"
        "nofail"
      ];
      depends = [ "/srv/media" ];
    };
  };
}
