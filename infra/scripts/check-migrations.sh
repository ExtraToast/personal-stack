#!/usr/bin/env bash
# Guard for Flyway-style SQL migrations. Enforces two invariants that, if
# broken, crashloop a service in production:
#
#   1. IMMUTABILITY — a migration that already exists on the base branch must
#      never be modified, renamed, or deleted. Flyway checksums each file in
#      full (comments included), so editing an applied migration breaks
#      `validate` on the next boot. Add a NEW migration instead. Override for
#      a deliberate, reviewed correction with ALLOW_MIGRATION_CHANGE=true.
#
#   2. VERSIONING — within each service, migration versions are unique, and
#      every newly added migration's version is strictly greater than the
#      highest version already on the base branch (incrementing; no reuse, no
#      backfilling a lower/middle version).
#
# Usage: check-migrations.sh [base-ref]   (default base: origin/main)
set -euo pipefail

base="${1:-origin/main}"
allow_change="${ALLOW_MIGRATION_CHANGE:-false}"

# V<version>__name.sql under any service's migration tree(s). knowledge-api
# splits versions across migration/ and migration-pg/, so both are in scope
# and share one version namespace per service.
mig_re='services/[^/]+/src/main/resources/db/migration(-pg)?/V[0-9][^/]*\.sql$'

fail=0

# Extract the numeric version token from a path: V12__foo.sql -> 12,
# V1_1__foo.sql -> 1.1 (dotted form sorts correctly under `sort -V`).
verkey() { basename "$1" | sed -E 's/^V([0-9]+(_[0-9]+)*)__.*/\1/; s/_/./g'; }

# Return 0 iff $1 > $2 as version strings.
ver_gt() { [[ "$1" != "$2" && "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -1)" == "$1" ]]; }

# --- Invariant 1: immutability of existing migrations ---
changed="$(git diff --diff-filter=MDR --name-only "${base}...HEAD" | { grep -E "$mig_re" || true; })"
if [[ -n "$changed" ]]; then
  if [[ "$allow_change" == "true" ]]; then
    echo "::warning::Existing migrations changed — allowed via ALLOW_MIGRATION_CHANGE override:"
    echo "$changed" | sed 's/^/  - /'
  else
    echo "::error::Applied migrations are immutable. Do not modify, rename, or delete a committed migration — add a NEW migration with a higher version. Offending files:"
    echo "$changed" | sed 's/^/  - /'
    fail=1
  fi
fi

# --- Invariant 2: unique + incrementing versions, per service ---
head_files="$(git ls-files | { grep -E "$mig_re" || true; })"
services="$(printf '%s\n' "$head_files" | sed -E 's#(services/[^/]+)/.*#\1#' | sort -u)"
base_all="$(git ls-tree -r --name-only "$base" | { grep -E "$mig_re" || true; })"

for svc in $services; do
  [[ -z "$svc" ]] && continue
  svc_re="^${svc}/src/main/resources/db/migration(-pg)?/V[0-9][^/]*\.sql$"
  cur="$(printf '%s\n' "$head_files" | { grep -E "$svc_re" || true; })"
  base_svc="$(printf '%s\n' "$base_all" | { grep -E "$svc_re" || true; })"

  # duplicate versions within the service (across both migration dirs)
  dups="$(for f in $cur; do verkey "$f"; done | sort | uniq -d)"
  if [[ -n "$dups" ]]; then
    echo "::error::[${svc##*/}] duplicate migration version(s): $(printf '%s ' $dups)"
    fail=1
  fi

  # newly added migrations must exceed the highest version already on base
  base_max="$(for f in $base_svc; do [[ -n "$f" ]] && verkey "$f"; done | sort -V | tail -1)"
  [[ -z "$base_max" ]] && continue
  for f in $cur; do
    printf '%s\n' "$base_svc" | grep -qxF "$f" && continue   # not newly added
    v="$(verkey "$f")"
    if ! ver_gt "$v" "$base_max"; then
      echo "::error::[${svc##*/}] new migration $(basename "$f") (version $v) must be greater than the highest existing version ($base_max)"
      fail=1
    fi
  done
done

if [[ $fail -eq 0 ]]; then
  echo "Migration guard: OK"
fi
exit $fail
