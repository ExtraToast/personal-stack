#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/../lib/host-env.sh"

usage() {
  echo "Usage: [PLATFORM_PI_IMAGE_OUT_DIR=<dir>] [PLATFORM_PI_IMAGE_OUT_LINK=<out-link>] [PLATFORM_PI_DOCKER_IMAGE=<image>] [PLATFORM_PI_DOCKER_VOLUME=<volume>] $(basename "$0") <node-name>" >&2
  exit 1
}

require_single_node_arg "$(basename "$0")" "$@"
load_host_env "$1"

if [[ "${NODE_ARCH:-}" != "arm64" ]]; then
  echo "Node ${NODE_NAME} is ${NODE_ARCH:-unknown}, not an arm64 Raspberry Pi image target" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required but not on PATH" >&2
  exit 1
fi

repo_root="$(platform_repo_root)"
out_dir="${PLATFORM_PI_IMAGE_OUT_DIR:-${repo_root}/build/pi-images}"
out_link="${PLATFORM_PI_IMAGE_OUT_LINK:-${out_dir}/.nix-result-${NODE_NAME}}"
docker_image="${PLATFORM_PI_DOCKER_IMAGE:-nixos/nix:latest}"
docker_volume="${PLATFORM_PI_DOCKER_VOLUME:-personal-stack-pi-nix-store}"

mkdir -p "${out_dir}"

out_link_rel="${out_link#${repo_root}/}"
if [[ "${out_link_rel}" == "${out_link}" ]]; then
  echo "PLATFORM_PI_IMAGE_OUT_LINK must live under the repository root (${repo_root})" >&2
  exit 1
fi

echo "Building Pi SD image for ${NODE_NAME} in linux/arm64 container (${docker_image})"
echo "Persistent Nix store volume: ${docker_volume}"
echo "Output directory: ${out_dir}"

out_dir_rel="${out_dir#${repo_root}/}"
if [[ "${out_dir_rel}" == "${out_dir}" ]]; then
  echo "PLATFORM_PI_IMAGE_OUT_DIR must live under the repository root (${repo_root})" >&2
  exit 1
fi

docker run --rm \
  --platform linux/arm64 \
  -v "${repo_root}:/work" \
  -v "${docker_volume}:/nix" \
  -w /work/platform \
  "${docker_image}" \
  sh -eu -c "
    nix --extra-experimental-features 'nix-command flakes' \
      build 'path:/work/platform#piSdImages.${NODE_NAME}' \
      --out-link '/work/${out_link_rel}' \
      --print-build-logs
    src=\$(readlink -f '/work/${out_link_rel}')
    if [ -d \"\$src/sd-image\" ]; then
      src=\$(find \"\$src/sd-image\" -maxdepth 1 -type f \\( -name '*.img' -o -name '*.img.zst' \\) | head -n 1)
    fi
    case \"\$src\" in
      *.img.zst) dst='/work/${out_dir_rel}/${NODE_NAME}.img.zst' ;;
      *.img)     dst='/work/${out_dir_rel}/${NODE_NAME}.img' ;;
      *) echo \"Unexpected output: \$src\" >&2; exit 1 ;;
    esac
    install -m 0644 \"\$src\" \"\$dst\"
  "

final_name="${NODE_NAME}.img.zst"
final_path="${out_dir}/${final_name}"
if [[ ! -f "${final_path}" ]]; then
  final_name="${NODE_NAME}.img"
  final_path="${out_dir}/${final_name}"
fi
if [[ ! -f "${final_path}" ]]; then
  echo "Expected image not found in ${out_dir}" >&2
  exit 1
fi

echo "Built Raspberry Pi SD image for ${NODE_NAME}:"
echo "  ${final_path}"
echo "Flash with e.g.:"
if [[ "${final_path}" == *.zst ]]; then
  echo "  zstd -d --stdout '${final_path}' | sudo dd of=/dev/diskN bs=4M status=progress"
else
  echo "  sudo dd if='${final_path}' of=/dev/diskN bs=4M status=progress"
fi