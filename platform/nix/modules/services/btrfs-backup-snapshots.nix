{ pkgs, ... }:
let
  srcSubvol = "/srv/media/Backup";
  srcSnapDir = "/srv/media/.snapshots";
  dstParent = "/srv/backup/.snapshots";
  dstSnapDir = "${dstParent}/Backup";
in
{
  systemd.services.btrfs-backup-snapshots = {
    description = "Weekly btrfs snapshot of /srv/media/Backup to /srv/backup";

    # btrfs-progs ships /run/current-system/sw/bin/btrfs only when on
    # environment.systemPackages; for unit-private PATH we list it here
    # explicitly. coreutils, findutils, gawk supply date / find / awk.
    path = [
      pkgs.btrfs-progs
      pkgs.coreutils
      pkgs.findutils
      pkgs.gawk
    ];

    unitConfig = {
      RequiresMountsFor = [
        "/srv/media"
        "/srv/backup"
        dstParent
      ];
    };

    serviceConfig = {
      Type = "oneshot";
      Nice = 19;
      IOSchedulingClass = "idle";
      IOSchedulingPriority = 7;
      # Full first send of ~120 GiB over local NVMe finishes in well
      # under an hour; cap at 4 h so a wedged ioctl can't run forever.
      TimeoutStartSec = "4h";

      # Modest hardening. Aggressive sandboxing (DynamicUser, capability
      # carving, ProtectSystem=strict without ReadWritePaths) interacts
      # badly with btrfs ioctls, so keep it readable.
      ProtectSystem = "strict";
      ProtectHome = "read-only";
      ReadWritePaths = [
        srcSnapDir
        dstParent
      ];
      PrivateTmp = true;
      NoNewPrivileges = true;
      ProtectKernelTunables = true;
      ProtectKernelModules = true;
      ProtectControlGroups = true;
      RestrictSUIDSGID = true;
      LockPersonality = true;
    };

    script = ''
      set -euo pipefail

      SRC_SUBVOL=${srcSubvol}
      SRC_SNAP_DIR=${srcSnapDir}
      DST_SNAP_DIR=${dstSnapDir}
      KEEP_SRC=4    # ~1 month of weekly snapshots locally
      KEEP_DST=26   # ~6 months on the NVMe

      # KEEP_SRC < 2 would drop the local incremental parent on prune
      # and force a full ~120 GiB send next week.
      if (( KEEP_SRC < 2 )); then
        echo "FATAL: KEEP_SRC must be >= 2" >&2
        exit 1
      fi

      mkdir -p "$DST_SNAP_DIR"

      # A killed btrfs receive leaves a writable, non-Received-UUID-tagged
      # subvol on the destination. The parent finder below would mis-pick
      # it. Sweep before doing anything else — idempotent recovery.
      while IFS= read -r entry; do
        recv_uuid=$(btrfs subvolume show "$entry" 2>/dev/null \
          | awk '/Received UUID:/ {print $3}' || true)
        if [[ -z "$recv_uuid" || "$recv_uuid" == "-" ]]; then
          echo "scrubbing partial received subvol: $entry"
          btrfs subvolume delete "$entry"
        fi
      done < <(find "$DST_SNAP_DIR" -mindepth 1 -maxdepth 1 -name 'Backup-*' -printf '%p\n')

      stamp=$(date -u +%Y%m%dT%H%M%SZ)
      new_snap="$SRC_SNAP_DIR/Backup-$stamp"

      btrfs subvolume snapshot -r "$SRC_SUBVOL" "$new_snap"

      # Most-recent local snapshot whose counterpart on the destination
      # has a valid Received UUID → incremental parent. Skip orphaned or
      # partial dest entries.
      parent=""
      while IFS= read -r src_name; do
        src_path="$SRC_SNAP_DIR/$src_name"
        dst_path="$DST_SNAP_DIR/$src_name"
        if [[ "$src_path" == "$new_snap" ]]; then continue; fi
        if [[ ! -d "$dst_path" ]]; then continue; fi
        recv_uuid=$(btrfs subvolume show "$dst_path" 2>/dev/null \
          | awk '/Received UUID:/ {print $3}' || true)
        if [[ -z "$recv_uuid" || "$recv_uuid" == "-" ]]; then continue; fi
        parent="$src_path"
        break
      done < <(find "$SRC_SNAP_DIR" -mindepth 1 -maxdepth 1 -name 'Backup-*' -printf '%f\n' | sort -r)

      if [[ -n "$parent" ]]; then
        echo "incremental send: parent=$parent -> $new_snap"
        btrfs send -p "$parent" "$new_snap" | btrfs receive "$DST_SNAP_DIR"
      else
        echo "WARNING: no valid parent found - sending full snapshot ($new_snap, ~120 GiB)"
        btrfs send "$new_snap" | btrfs receive "$DST_SNAP_DIR"
      fi

      prune() {
        local dir="$1" keep="$2" label="$3"
        mapfile -t snaps < <(find "$dir" -mindepth 1 -maxdepth 1 -name 'Backup-*' -printf '%f\n' | sort)
        local n=''${#snaps[@]}
        if (( n > keep )); then
          local to_remove=$(( n - keep ))
          echo "pruning $to_remove old $label snapshot(s) (keep $keep of $n)"
          for s in "''${snaps[@]:0:to_remove}"; do
            btrfs subvolume delete "$dir/$s"
          done
        fi
      }
      prune "$SRC_SNAP_DIR" "$KEEP_SRC" source
      prune "$DST_SNAP_DIR" "$KEEP_DST" destination
    '';
  };

  systemd.timers.btrfs-backup-snapshots = {
    description = "Weekly trigger for btrfs-backup-snapshots";
    wantedBy = [ "timers.target" ];
    timerConfig = {
      OnCalendar = "Sun 03:00";
      # Catch up if the host was off at scheduled time (reboots,
      # planned maintenance) — without this a Sunday-afternoon
      # power-on silently skips the week.
      Persistent = true;
      RandomizedDelaySec = "30m";
      AccuracySec = "1min";
    };
  };
}
