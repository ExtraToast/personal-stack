#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "Usage: $0 <command> [args...]" >&2
  exit 64
fi

OUTPUT_FILE="$(mktemp)"
trap 'rm -f "$OUTPUT_FILE"' EXIT

set +e
"$@" > >(tee "$OUTPUT_FILE") 2> >(tee -a "$OUTPUT_FILE" >&2)
COMMAND_EXIT=$?
set -e

if grep -Eq 'A terminally deprecated method in sun\.misc\.Unsafe has been called|sun\.misc\.Unsafe::' "$OUTPUT_FILE"; then
  echo "[STRICT] Forbidden sun.misc.Unsafe warning detected." >&2
  exit 1
fi

if grep -Eq 'Deprecated Gradle features were used in this build|This behavior has been deprecated\.' "$OUTPUT_FILE"; then
  echo "[STRICT] Forbidden Gradle deprecation warning detected." >&2
  exit 1
fi

exit "$COMMAND_EXIT"
