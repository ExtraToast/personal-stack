#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

for test_script in "$SCRIPT_DIR"/*_test.sh; do
  sh "$test_script"
done
