{ ... }:
{
  systemd.tmpfiles.rules = [
    "d /srv/media 0775 deploy deploy - -"
    "d /srv/media/Completed 0775 deploy deploy - -"
    "d /srv/media/Downloading 0775 deploy deploy - -"
    "d /srv/media/Films 0775 deploy deploy - -"
    "d /srv/media/Series 0775 deploy deploy - -"
    "d /srv/media/Anime 0775 deploy deploy - -"
    "d /srv/media/TimeMachine 0775 deploy deploy - -"
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
}
