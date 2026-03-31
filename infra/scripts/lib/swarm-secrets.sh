#!/usr/bin/env bash
# swarm-secrets.sh — Versioned Docker Swarm secret management for zero-downtime rotation.
#
# Docker Swarm secrets are immutable. To rotate a secret without tearing down the
# entire stack, we use versioned names: secret_name_v1, secret_name_v2, etc.
# A compose overlay file maps the versioned name to the expected mount path.
#
# Usage:
#   source lib/swarm-secrets.sh
#   init_secret_versions              # load or create version file
#   create_versioned_secret "name" "value"  # creates name_v<N+1>
#   current_secret_ref "name"         # returns name_v<N> (for compose overlay)
#   cleanup_old_secret "name"         # removes name_v<N-1> if it exists

VERSIONS_FILE="${VERSIONS_FILE:-/opt/personal-stack/.secret-versions}"

# ── Version file management ─────────────────────────────────────────────────

init_secret_versions() {
  if [[ ! -f "$VERSIONS_FILE" ]]; then
    echo '{}' > "$VERSIONS_FILE"
    chmod 600 "$VERSIONS_FILE"
  fi
}

_get_version() {
  local name="$1"
  if command -v jq > /dev/null 2>&1; then
    jq -r --arg k "$name" '.[$k] // 0' "$VERSIONS_FILE"
  elif command -v python3 > /dev/null 2>&1; then
    python3 -c "import json,sys; d=json.load(open('$VERSIONS_FILE')); print(d.get('$name', 0))"
  else
    # Fallback: grep-based parsing for simple JSON
    local val
    val=$(grep -o "\"${name}\":[0-9]*" "$VERSIONS_FILE" | cut -d: -f2)
    echo "${val:-0}"
  fi
}

_set_version() {
  local name="$1" version="$2"
  if command -v jq > /dev/null 2>&1; then
    local tmp
    tmp=$(mktemp)
    jq --arg k "$name" --argjson v "$version" '.[$k] = $v' "$VERSIONS_FILE" > "$tmp" \
      && mv "$tmp" "$VERSIONS_FILE"
  elif command -v python3 > /dev/null 2>&1; then
    python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    d = json.load(f)
d['$name'] = $version
with open('$VERSIONS_FILE', 'w') as f:
    json.dump(d, f, indent=2)
"
  else
    die "jq or python3 is required for secret versioning"
  fi
  chmod 600 "$VERSIONS_FILE"
}

# ── Secret operations ─────────────────────────────────────────────────────────

# Create a new versioned secret, incrementing the version counter.
# Returns the new versioned secret name.
create_versioned_secret() {
  local name="$1" value="$2"
  local current_ver new_ver new_name

  current_ver=$(_get_version "$name")
  new_ver=$((current_ver + 1))
  new_name="${name}_v${new_ver}"

  printf '%s' "$value" | docker secret create "$new_name" - > /dev/null
  _set_version "$name" "$new_ver"

  echo "$new_name"
}

# Get the current versioned secret name for use in compose files.
current_secret_ref() {
  local name="$1"
  local ver
  ver=$(_get_version "$name")
  if [[ "$ver" -eq 0 ]]; then
    # No versioned secret exists yet; use the base name (legacy)
    echo "$name"
  else
    echo "${name}_v${ver}"
  fi
}

# Remove the previous version of a secret (N-1) if it exists.
cleanup_old_secret() {
  local name="$1"
  local ver old_name
  ver=$(_get_version "$name")
  if [[ "$ver" -gt 1 ]]; then
    old_name="${name}_v$((ver - 1))"
    docker secret rm "$old_name" 2>/dev/null || true
  fi
}

# Migrate a legacy (unversioned) secret to the versioned scheme.
# Creates name_v1 with the same value, sets version to 1.
migrate_to_versioned() {
  local name="$1"
  local ver
  ver=$(_get_version "$name")
  if [[ "$ver" -gt 0 ]]; then
    return 0  # Already versioned
  fi

  # Read current value from the running container or skip
  local value
  value=$(docker secret inspect "$name" --format '{{.ID}}' 2>/dev/null) || true
  if [[ -z "$value" ]]; then
    return 1  # Secret doesn't exist
  fi

  # We can't read the value of an existing Docker secret, so we need
  # the caller to provide it. Just set the version counter.
  _set_version "$name" 0
}

# List all secrets with their current versions.
list_secret_versions() {
  if command -v jq > /dev/null 2>&1; then
    jq -r 'to_entries[] | "\(.key): v\(.value)"' "$VERSIONS_FILE"
  elif command -v python3 > /dev/null 2>&1; then
    python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    d = json.load(f)
for k, v in sorted(d.items()):
    print(f'{k}: v{v}')
"
  else
    cat "$VERSIONS_FILE"
  fi
}
