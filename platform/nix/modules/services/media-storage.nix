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
    # NVMe scratch for in-progress torrent writes (see fileSystems below).
    "d /srv/media-incomplete 0775 deploy deploy - -"
    "z /srv/media-incomplete 0775 deploy deploy - -"
    # nodatacow for the scratch subvolume, set as a directory attribute so
    # new incomplete files inherit it (the mount option can't override the
    # filesystem-wide CoW/compress — see fileSystems below). Avoids btrfs
    # CoW fragmentation and pointless zstd on incompressible torrent data.
    "h /srv/media-incomplete - - - - +C"
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
    "/srv/media-incomplete" = {
      # NVMe scratch for qBittorrent in-progress (incomplete) torrent data:
      # the @media-incomplete subvolume on the backup pool (LABEL=backup,
      # the 512 GB MZVLB M.2), a sibling of @backup / @snapshots. The
      # subvolume itself is declared in hosts/enschede-t1000-1/disko-backup.nix
      # so disko creates it at install on any fresh node; this entry just
      # mounts it. Out-of-order torrent piece writes are random I/O the
      # 5400-class /srv/media HDD pool handles poorly; landing them on the SSD
      # and moving the finished file to the HDD save path in one sequential
      # pass removes the download bottleneck.
      #
      # No `nodatacow` mount option here: btrfs CoW/compression are
      # filesystem-wide and @backup already mounted the fs `compress=zstd:3`,
      # so a second subvolume mount cannot override them. nodatacow is applied
      # per-file via the tmpfiles `h … +C` rule above instead (it works on a
      # datacow fs and new files inherit it). nofail keeps a missing subvolume
      # from wedging local-fs.target.
      device = "/dev/disk/by-label/backup";
      fsType = "btrfs";
      options = [
        "subvol=@media-incomplete"
        "noatime"
        "nofail"
        "x-systemd.device-timeout=10s"
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

  # Own + cap the NVMe scratch subvolume.
  #
  # Ownership: @media-incomplete is created owned root:root (btrfs subvolume
  # create / disko). The tmpfiles `z` above races the `nofail` NVMe mount
  # (systemd-tmpfiles runs before the late mount completes, so it chowns the
  # bare mountpoint, not the mounted subvolume) — which left the subvolume
  # root root:root and broke qBittorrent (UID 1000) with "Permission denied"
  # on every download. This service is the authoritative fix: RequiresMountsFor
  # orders it strictly after the mount, so the chown always lands on the
  # mounted subvolume root. Runs on every boot / nixos-rebuild, so it persists
  # across deploys and provisions fresh nodes correctly.
  #
  # Quota: cap in-flight downloads so they can never fill the shared backup
  # pool. Non-fatal (`-`): a freshly formatted pool may not have quotas on yet.
  systemd.services."media-incomplete-setup" = {
    description = "Own and quota the /srv/media-incomplete scratch subvolume";
    wantedBy = [ "multi-user.target" ];
    unitConfig.RequiresMountsFor = [ "/srv/media-incomplete" ];
    serviceConfig = {
      Type = "oneshot";
      RemainAfterExit = true;
      ExecStart = [
        # UID/GID 1000 = the deploy user = qBittorrent's PUID/PGID.
        "${pkgs.coreutils}/bin/chown 1000:1000 /srv/media-incomplete"
        "${pkgs.coreutils}/bin/chmod 0775 /srv/media-incomplete"
        "-${pkgs.btrfs-progs}/bin/btrfs quota enable /srv/media-incomplete"
        "-${pkgs.btrfs-progs}/bin/btrfs qgroup limit 250G /srv/media-incomplete"
      ];
    };
  };
}
