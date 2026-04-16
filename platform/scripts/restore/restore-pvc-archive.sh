#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF' >&2
Usage: restore-pvc-archive.sh --namespace <ns> (--pvc <name> | --pvc-match <substring>) --archive <file.tar.gz> [options]

Restores a tar.gz archive into the root of a mounted PVC by streaming the archive
from the local machine into a temporary restore pod in the cluster.

Options:
  --namespace <ns>         Kubernetes namespace containing the PVC.
  --pvc <name>             Exact PVC name.
  --pvc-match <substring>  Resolve the PVC by matching a unique substring.
  --archive <file>         Local .tar.gz archive to restore.
  --strip-components <n>   Tar strip count before writing into the PVC root. Default: 0.
  --pod-name <name>        Temporary restore pod name. Default: restore-<pvc>.
  --image <image>          Restore pod image. Default: debian:bookworm-slim.
  --wipe-target            Delete existing PVC contents before extraction.
  --keep-pod               Leave the restore pod behind after completion.
EOF
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing command: $1" >&2
    exit 1
  }
}

NAMESPACE=""
PVC_NAME=""
PVC_MATCH=""
ARCHIVE=""
STRIP_COMPONENTS=0
POD_NAME=""
IMAGE="debian:bookworm-slim"
WIPE_TARGET="false"
KEEP_POD="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace)
      NAMESPACE="${2:?Missing value for --namespace}"
      shift 2
      ;;
    --pvc)
      PVC_NAME="${2:?Missing value for --pvc}"
      shift 2
      ;;
    --pvc-match)
      PVC_MATCH="${2:?Missing value for --pvc-match}"
      shift 2
      ;;
    --archive)
      ARCHIVE="${2:?Missing value for --archive}"
      shift 2
      ;;
    --strip-components)
      STRIP_COMPONENTS="${2:?Missing value for --strip-components}"
      shift 2
      ;;
    --pod-name)
      POD_NAME="${2:?Missing value for --pod-name}"
      shift 2
      ;;
    --image)
      IMAGE="${2:?Missing value for --image}"
      shift 2
      ;;
    --wipe-target)
      WIPE_TARGET="true"
      shift
      ;;
    --keep-pod)
      KEEP_POD="true"
      shift
      ;;
    -h|--help)
      usage
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      ;;
  esac
done

require_command kubectl
require_command jq

[[ -n "${NAMESPACE}" ]] || usage
[[ -n "${ARCHIVE}" ]] || usage
[[ -f "${ARCHIVE}" ]] || {
  echo "Archive not found: ${ARCHIVE}" >&2
  exit 1
}

if [[ -n "${PVC_NAME}" && -n "${PVC_MATCH}" ]]; then
  echo "Use either --pvc or --pvc-match, not both." >&2
  exit 1
fi

if [[ -z "${PVC_NAME}" && -z "${PVC_MATCH}" ]]; then
  echo "One of --pvc or --pvc-match is required." >&2
  exit 1
fi

if [[ -n "${PVC_MATCH}" ]]; then
  mapfile -t pvc_matches < <(
    kubectl get pvc -n "${NAMESPACE}" -o json \
      | jq -r '.items[].metadata.name' \
      | awk -v needle="${PVC_MATCH}" 'index($0, needle) > 0'
  )

  if [[ "${#pvc_matches[@]}" -eq 0 ]]; then
    echo "No PVC in namespace ${NAMESPACE} matched substring: ${PVC_MATCH}" >&2
    exit 1
  fi

  if [[ "${#pvc_matches[@]}" -gt 1 ]]; then
    printf 'PVC match %s was ambiguous in %s:\n' "${PVC_MATCH}" "${NAMESPACE}" >&2
    printf '  %s\n' "${pvc_matches[@]}" >&2
    exit 1
  fi

  PVC_NAME="${pvc_matches[0]}"
fi

kubectl get pvc -n "${NAMESPACE}" "${PVC_NAME}" >/dev/null

if [[ -z "${POD_NAME}" ]]; then
  POD_NAME="restore-${PVC_NAME//[^a-zA-Z0-9-]/-}"
fi

cleanup() {
  if [[ "${KEEP_POD}" == "false" ]]; then
    kubectl delete pod -n "${NAMESPACE}" "${POD_NAME}" --ignore-not-found --wait=true >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "Preparing restore pod ${POD_NAME} for PVC ${PVC_NAME} in namespace ${NAMESPACE}"
kubectl delete pod -n "${NAMESPACE}" "${POD_NAME}" --ignore-not-found --wait=true >/dev/null 2>&1 || true

cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: ${POD_NAME}
  namespace: ${NAMESPACE}
  labels:
    personal-stack/restore: "true"
spec:
  restartPolicy: Never
  containers:
    - name: restore
      image: ${IMAGE}
      command:
        - /bin/sh
        - -ec
        - |
          trap : TERM INT
          sleep infinity & wait
      volumeMounts:
        - name: data
          mountPath: /restore
  volumes:
    - name: data
      persistentVolumeClaim:
        claimName: ${PVC_NAME}
EOF

kubectl wait -n "${NAMESPACE}" --for=condition=Ready "pod/${POD_NAME}" --timeout=180s >/dev/null

if [[ "${WIPE_TARGET}" == "true" ]]; then
  echo "Wiping existing contents from PVC ${PVC_NAME}"
  kubectl exec -n "${NAMESPACE}" "${POD_NAME}" -- sh -ec '
    find /restore -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
  '
fi

echo "Restoring $(basename "${ARCHIVE}") into PVC ${PVC_NAME}"
cat "${ARCHIVE}" | kubectl exec -i -n "${NAMESPACE}" "${POD_NAME}" -- \
  tar -xzpf - -C /restore --strip-components "${STRIP_COMPONENTS}"

echo "PVC restore finished: ${NAMESPACE}/${PVC_NAME}"
