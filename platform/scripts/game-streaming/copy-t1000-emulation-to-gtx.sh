#!/usr/bin/env bash
set -euo pipefail

t1000_host="${T1000_HOST:-100.103.175.110}"
gtx_host="${GTX_HOST:-100.89.41.92}"
ssh_port="${SSH_PORT:-2222}"
source_subpath="${SOURCE_SUBPATH:-Emulation}"
dest_root="${DEST_ROOT:-/srv/game-streaming/imports/t1000}"
execute=false

usage() {
  cat <<EOF
Usage: $0 [--execute]

Copies /srv/media/\${SOURCE_SUBPATH} from enschede-t1000-1 to the GTX 960M
SSD import area at \${DEST_ROOT}/\${SOURCE_SUBPATH}.

Environment overrides:
  T1000_HOST       default: 100.103.175.110
  GTX_HOST         default: 100.89.41.92
  SSH_PORT         default: 2222
  SOURCE_SUBPATH   default: Emulation
  DEST_ROOT        default: /srv/game-streaming/imports/t1000

The destination must stay outside /srv/games. /srv/games is the protected
external Wii/GameCube FAT32 disk.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --execute)
      execute=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

case "$dest_root" in
  /srv/games|/srv/games/*)
    echo "Refusing to copy into protected external Wii/GameCube disk: $dest_root" >&2
    exit 1
    ;;
esac

echo "Source:      deploy@${t1000_host}:/srv/media/${source_subpath}"
echo "Destination: deploy@${gtx_host}:${dest_root}/${source_subpath}"

if [[ "$execute" != true ]]; then
  echo
  echo "Dry run only. Add --execute to copy with tar over SSH."
  echo
  ssh -p "$ssh_port" "deploy@${t1000_host}" \
    "sudo find '/srv/media/${source_subpath}' -maxdepth 2 -mindepth 1 -printf '%y %p\n' 2>/dev/null | sort | sed -n '1,120p'"
  exit 0
fi

ssh -p "$ssh_port" "deploy@${gtx_host}" \
  "sudo mkdir -p '${dest_root}' && sudo test '${dest_root}' != /srv/games"

ssh -p "$ssh_port" "deploy@${t1000_host}" \
  "sudo tar -C /srv/media -cf - '${source_subpath}'" \
  | ssh -p "$ssh_port" "deploy@${gtx_host}" \
      "sudo tar -C '${dest_root}' -xpf - && sudo chown -R gamehost:users '${dest_root}/${source_subpath}'"
