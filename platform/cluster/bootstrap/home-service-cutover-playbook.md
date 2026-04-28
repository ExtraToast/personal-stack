# Home Service Cutover Playbook

This playbook covers the first real cutover from the current home utility node
onto `enschede-t1000-1`.

Scope:

- media disk and library data
- media application config and history
- Samba shares on the new node

This deliberately does not depend on preserving `AdGuard Home` state. If the
existing instance has little or no state, rebuild it later instead of letting
that block the T1000 cutover.

## Current To Target Mapping

The old home-node layout and the new `NixOS` layout are not path-compatible.
Plan the move explicitly.

- Old media disk mount: `/mnt/media`
- New media disk mount: `/srv/media`
- Old media config roots: `/srv/nomad/qbittorrent`, `/srv/nomad/prowlarr`, `/srv/nomad/bazarr`, `/srv/nomad/sonarr`, `/srv/nomad/radarr`, `/srv/nomad/jellyfin`, `/srv/nomad/jellyseerr`
- New media config roots: `/var/lib/personal-stack/media/qbittorrent`, `/var/lib/personal-stack/media/prowlarr`, `/var/lib/personal-stack/media/bazarr`, `/var/lib/personal-stack/media/sonarr`, `/var/lib/personal-stack/media/radarr`, `/var/lib/personal-stack/media/jellyfin`, `/var/lib/personal-stack/media/jellyseerr`

## Pre-Cutover Checklist

1. Add `bootstrap_ssh` for `enschede-t1000-1` in [fleet.yaml](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/inventory/fleet.yaml:1) with the real current `RHEL` SSH endpoint.
2. Decide how the media disk will arrive on the T1000.
   Preferred path:
   Move the existing disk physically to the T1000 so the actual media files do not need a full LAN copy.
3. Verify the disk identity before install.
   The current host definition expects [disko.nix](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/nix/hosts/enschede-t1000-1/disko.nix:1) to mount `/dev/disk/by-label/DataBeast` at `/srv/media`.
   The current home media hosts assume `ntfs3` with compatibility mount options matching the existing home-node setup.
   If the current disk is not labeled `DataBeast`, patch the host definition to use the real UUID or relabel the filesystem before the cutover (`sudo ntfslabel /dev/sdX DataBeast`).
4. Export or snapshot the old media config directories before you stop anything:
   `sudo rsync -a /srv/nomad/qbittorrent/ /tmp/home-cutover/qbittorrent/`
   `sudo rsync -a /srv/nomad/prowlarr/ /tmp/home-cutover/prowlarr/`
   `sudo rsync -a /srv/nomad/bazarr/ /tmp/home-cutover/bazarr/`
   `sudo rsync -a /srv/nomad/sonarr/ /tmp/home-cutover/sonarr/`
   `sudo rsync -a /srv/nomad/radarr/ /tmp/home-cutover/radarr/`
   `sudo rsync -a /srv/nomad/jellyfin/ /tmp/home-cutover/jellyfin/`
   `sudo rsync -a /srv/nomad/jellyseerr/ /tmp/home-cutover/jellyseerr/`
5. Confirm the `Vault` media secret already exists for the new downloads pod:
   `secret/data/platform/media`

## Bring Up The T1000

1. Install `NixOS` using [home-install-playbook.md](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/cluster/bootstrap/home-install-playbook.md:1).
2. Verify the T1000 post-install prerequisites:
   `ssh -p 2222 deploy@enschede-t1000-1 hostnamectl`
   `ssh -p 2222 deploy@enschede-t1000-1 nvidia-smi`
   `ssh -p 2222 deploy@enschede-t1000-1 mount | grep /srv/media`
   `kubectl get node enschede-t1000-1 --show-labels`
3. Confirm the host prep module created the expected paths:
   `ssh -p 2222 deploy@enschede-t1000-1 sudo find /srv/media -maxdepth 1 -type d | sort`
   `ssh -p 2222 deploy@enschede-t1000-1 sudo find /var/lib/personal-stack/media -maxdepth 1 -type d | sort`

## Media Data Migration

If the original media disk moves to the T1000 intact, you only need to verify
that `/srv/media` points at that disk and that the expected top-level
directories exist.

If you must copy over the network instead:

1. Stop writes on the old node first.
2. Run a bulk copy:
   `sudo rsync -aHAX --info=progress2 old-home-node:/mnt/media/ /srv/media/`
3. Repeat one final short rsync during the cutover window after the old
   workloads have been stopped.

## Media Config Migration

Stop the old Nomad jobs before the final config copy so SQLite databases and
download state are consistent.

Suggested stop set:

- `downloads`
- `bazarr`
- `sonarr`
- `radarr`
- `jellyfin`
- `jellyseerr`

Copy the config roots onto the T1000:

- `/srv/nomad/qbittorrent/` -> `/var/lib/personal-stack/media/qbittorrent/`
- `/srv/nomad/prowlarr/` -> `/var/lib/personal-stack/media/prowlarr/`
- `/srv/nomad/bazarr/` -> `/var/lib/personal-stack/media/bazarr/`
- `/srv/nomad/sonarr/` -> `/var/lib/personal-stack/media/sonarr/`
- `/srv/nomad/radarr/` -> `/var/lib/personal-stack/media/radarr/`
- `/srv/nomad/jellyfin/` -> `/var/lib/personal-stack/media/jellyfin/`
- `/srv/nomad/jellyseerr/` -> `/var/lib/personal-stack/media/jellyseerr/`

Example:

`sudo rsync -a old-home-node:/srv/nomad/qbittorrent/ /var/lib/personal-stack/media/qbittorrent/`

After restore, check ownership:

`sudo chown -R 1000:1000 /var/lib/personal-stack/media`

## Samba Bring-Up

The `utility` profile now prepares the legacy share layout on the T1000:

- `media-admin` -> `/srv/media`
  - Valid user: `media-root`
  - Purpose: all-access administrative view of the DataBeast media volume.
- `media-downloads` -> `/srv/media-views/media-downloads`
  - Valid users: `media-root`, `media-downloads`
  - Contains bind-mounted `Downloading` and `Completed`.
- `media-tv` -> `/srv/media-views/media-tv`
  - Valid users: `media-root`, `media-tv`
  - Contains bind-mounted `Completed` and `Series`.
- `media-movies` -> `/srv/media-views/media-movies`
  - Valid users: `media-root`, `media-movies`
  - Contains bind-mounted `Completed` and `Films`.
- `media-library` -> `/srv/media-views/media-library`
  - Valid users: `media-root`, `media-library`
  - Read-only view of bind-mounted `Series` and `Films`.
- `timemachine` -> `/srv/media/TimeMachine`
  - Valid user: `media-root`

Before clients reconnect:

1. Set the all-access Samba password for `media-root`, reusing the password
   that previously protected the broad all-access media share:
   `ssh -p 2222 deploy@enschede-t1000-1 sudo smbpasswd -a media-root`
2. Validate the exported share list:
   `smbclient -L //enschede-t1000-1 -U media-root`
3. Validate write access to `media-admin`, `media-downloads`, `media-tv`,
   and `media-movies`.
4. Validate that `media-library` is browseable but read-only.
5. Add Samba passwords for narrower role users only when a real client needs
   that narrower access:
   `media-downloads`, `media-tv`, `media-movies`, or `media-library`.

## Cutover Validation

After the old jobs are down and the new node is live:

1. Confirm the media workloads schedule onto `enschede-t1000-1`.
2. Open:
   `jellyfin.jorisjonkers.dev`
   `jellyseerr.jorisjonkers.dev`
   `radarr.jorisjonkers.dev`
   `sonarr.jorisjonkers.dev`
   `bazarr.jorisjonkers.dev`
   `prowlarr.jorisjonkers.dev`
   `qbittorrent.jorisjonkers.dev`
3. Confirm downloads still land under `/srv/media/Downloading` and complete
   into `/srv/media/Completed`.
4. Confirm Sonarr imports from `Completed` into `Series`.
5. Confirm Radarr imports from `Completed` into `Films`.
6. Confirm Jellyfin can scan, write metadata, and delete files in `Series`
   and `Films`.
7. Confirm Bazarr can read and write subtitle files in `Series` and `Films`.
8. Confirm Jellyseerr can read `Series` and `Films` without write access.
9. Confirm SMB clients can browse only the directories exposed by their role
   share.

Do not wipe the old home node until at least one real download, one library
scan, one playback test, and one SMB client write test have all succeeded.
