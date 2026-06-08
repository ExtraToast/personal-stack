{ lib, pkgs, ... }:
let
  mediaRootDirs = [
    "/srv/media"
    "/srv/media/Completed"
    "/srv/media/Downloading"
    "/srv/media/Films"
    "/srv/media/Series"
    "/srv/media/Anime"
    "/srv/media/TimeMachine"
    "/srv/media/Photos"
  ];
  mediaApplicationTrees = [
    "/srv/media/Completed"
    "/srv/media/Downloading"
    "/srv/media/Films"
    "/srv/media/Series"
    "/srv/media/Anime"
    "/srv/media/Photos"
  ];
  mediaConfigDirs = [
    "/var/lib/personal-stack"
    "/var/lib/personal-stack/media"
    "/var/lib/personal-stack/media/qbittorrent"
    "/var/lib/personal-stack/media/prowlarr"
    "/var/lib/personal-stack/media/bazarr"
    "/var/lib/personal-stack/media/sonarr"
    "/var/lib/personal-stack/media/radarr"
    "/var/lib/personal-stack/media/jellyfin"
    "/var/lib/personal-stack/media/jellyseerr"
    "/var/lib/personal-stack/media/immich"
    "/var/lib/personal-stack/media/immich-ml-cache"
  ];
in
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
    # `d` only sets owner/mode on creation; for directories that already
    # exist (e.g. carried over from the NTFS pool) it leaves perms alone.
    # The NTFS->btrfs migration left Downloading/Completed/Films/Series as
    # root:root 0755, which silently broke qBittorrent (UID 1000) creating
    # new files. Pair each `d` with `z` so subsequent activations realign
    # owner/mode on the directory inode (no recursion — leaves child
    # files' perms intact).
    "d /srv/media 0775 deploy deploy - -"
    "z /srv/media 0775 deploy deploy - -"
    "d /srv/media/Completed 0775 deploy deploy - -"
    "z /srv/media/Completed 0775 deploy deploy - -"
    "d /srv/media/Downloading 0775 deploy deploy - -"
    "z /srv/media/Downloading 0775 deploy deploy - -"
    "d /srv/media/Films 0775 deploy deploy - -"
    "z /srv/media/Films 0775 deploy deploy - -"
    "d /srv/media/Series 0775 deploy deploy - -"
    "z /srv/media/Series 0775 deploy deploy - -"
    "d /srv/media/Anime 0775 deploy deploy - -"
    "z /srv/media/Anime 0775 deploy deploy - -"
    "d /srv/media/TimeMachine 0775 deploy deploy - -"
    "z /srv/media/TimeMachine 0775 deploy deploy - -"
    "d /srv/media/Photos 0775 deploy deploy - -"
    "z /srv/media/Photos 0775 deploy deploy - -"
    "d /srv/media-views 0755 root root - -"
    # Parent dirs are regular directories on the rootfs — keep them
    # owned by root:root 0755. The /<leaf> entries below are the bind
    # mount *targets* for /srv/media/<Name>, so a `d` rule that sets
    # ownership on the leaf path writes through the bind into the
    # source inode under /srv/media. Match the source's intended
    # 0775 deploy:deploy here so each activation re-aligns the source
    # (alongside the existing `z` rules above) instead of resetting
    # /srv/media/{Downloading,Completed,Films,Series} back to
    # root:root 0755 — which silently breaks qBittorrent / *arr
    # (UID 1000) creating new files in /srv/media/Downloading.
    "d /srv/media-views/media-downloads 0755 root root - -"
    "d /srv/media-views/media-downloads/Downloading 0775 deploy deploy - -"
    "d /srv/media-views/media-downloads/Completed 0775 deploy deploy - -"
    "d /srv/media-views/media-series 0755 root root - -"
    "d /srv/media-views/media-series/Completed 0775 deploy deploy - -"
    "d /srv/media-views/media-series/Series 0775 deploy deploy - -"
    "d /srv/media-views/media-movies 0755 root root - -"
    "d /srv/media-views/media-movies/Completed 0775 deploy deploy - -"
    "d /srv/media-views/media-movies/Films 0775 deploy deploy - -"
    "d /srv/media-views/media-library 0755 root root - -"
    "d /srv/media-views/media-library/Series 0775 deploy deploy - -"
    "d /srv/media-views/media-library/Films 0775 deploy deploy - -"
    "d /var/lib/personal-stack 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/qbittorrent 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/prowlarr 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/bazarr 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/sonarr 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/radarr 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/jellyfin 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/jellyseerr 0755 deploy deploy - -"
    "d /var/lib/personal-stack/media/immich 0755 deploy deploy - -"
    # Postgres data dir must be 0700, owned by the image's postgres UID 70.
    "d /var/lib/personal-stack/media/immich-postgres 0700 70 70 - -"
    "d /var/lib/personal-stack/media/immich-ml-cache 0755 deploy deploy - -"
  ];

  systemd.services.personal-stack-media-permissions = {
    description = "Reconcile personal-stack media ownership after mounting /srv/media";
    wantedBy = [ "multi-user.target" ];
    before = [
      "k3s.service"
      "samba-smbd.service"
      "samba-nmbd.service"
    ];
    unitConfig.RequiresMountsFor = [ "/srv/media" ];
    path = [
      pkgs.coreutils
      pkgs.findutils
    ];
    serviceConfig = {
      Type = "oneshot";
      RemainAfterExit = true;
    };
    script = ''
      set -euo pipefail

      for dir in ${lib.escapeShellArgs mediaRootDirs}; do
        install -d -m 0775 -o deploy -g deploy "$dir"
      done

      for dir in ${lib.escapeShellArgs mediaConfigDirs}; do
        install -d -m 0755 -o deploy -g deploy "$dir"
      done
      install -d -m 0700 -o 70 -g 70 /var/lib/personal-stack/media/immich-postgres

      # These hostPath trees are shared by qBittorrent, Sonarr, Radarr,
      # Bazarr, Jellyfin, Jellyseerr/Seerr, and Immich. Keep the walk
      # scoped away from TimeMachine and backup subvolumes.
      find ${lib.escapeShellArgs mediaApplicationTrees} -xdev \
        \( ! -user 1000 -o ! -group 1000 \) \
        -exec chown 1000:1000 {} +
      find ${lib.escapeShellArgs mediaApplicationTrees} -xdev -type d \
        -exec chmod u+rwx,g+rwx {} +
    '';
  };

  fileSystems = {
    "/srv/media" = {
      # 2-device btrfs media-pool across both 6 TB HDDs (formerly NTFS
      # DataBeast + its successor). Data profile is `single` (no striping —
      # each chunk lives on one device, losing one disk loses only its
      # chunks); metadata is RAID1 across both devices.
      #
      # Only one `device=` is needed: udev's btrfs-scan tags both members
      # with the same filesystem UUID, so the kernel registers the second
      # device automatically and the mount finds it via UUID. Listing both
      # devices here additionally triggers a kernel re-scan on `mount -o
      # remount`, which can hang for minutes while a balance is in flight
      # — and NixOS issues a remount on every fstab option change.
      device = "/dev/disk/by-id/ata-WDC_WD60EDAZ-11CFNB0_WD-WX22DA54RL1P";
      fsType = "btrfs";
      options = [
        "noatime"
        "compress=zstd:3"
        "nofail"
      ];
    };
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
