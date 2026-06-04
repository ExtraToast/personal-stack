#!/usr/bin/env bash
# Prints a GitHub token suitable for this runner's repo. A pre-seeded
# GH_TOKEN/GITHUB_PERSONAL_ACCESS_TOKEN wins; otherwise the helper mints
# a short-lived GitHub App installation token through assistant-api and
# caches it until it is close to expiry.
set -u

CACHE="${GH_APP_TOKEN_CACHE:-/tmp/.gh-app-token}"
SKEW="${GH_APP_TOKEN_SKEW_SECONDS:-300}"
WORKSPACE_ROOT="${WORKSPACE_ROOT:-/workspace}"

parse_expiry() {
  local value="$1"
  date -d "$value" +%s 2>/dev/null ||
    date -j -f "%Y-%m-%dT%H:%M:%SZ" "$value" +%s 2>/dev/null ||
    echo 0
}

cached_token() {
  local now exp tok
  now=$(date +%s)
  [ -f "$CACHE" ] || return 1
  exp=$(sed -n 1p "$CACHE" 2>/dev/null)
  tok=$(sed -n 2p "$CACHE" 2>/dev/null)
  [ -n "$exp" ] && [ -n "$tok" ] && [ "$exp" -gt "$((now + SKEW))" ] 2>/dev/null || return 1
  printf '%s' "$tok"
}

mint_token() {
  [ -n "${GITHUB_APP_TOKEN_URL:-}" ] && [ -n "${GITHUB_APP_TOKEN_BEARER:-}" ] || return 1

  local repo_url payload resp tok exp_iso exp
  repo_url="$(repo_url)" || return 1
  [ -n "$repo_url" ] || return 1

  payload=$(jq -nc --arg repoUrl "$repo_url" '{repoUrl: $repoUrl}') || return 1
  resp=$(curl -fsS --max-time 10 -X POST "$GITHUB_APP_TOKEN_URL" \
    -H "Authorization: Bearer $GITHUB_APP_TOKEN_BEARER" \
    -H "Content-Type: application/json" \
    -d "$payload" 2>/dev/null) || return 1

  tok=$(printf '%s' "$resp" | jq -r '.token // empty' 2>/dev/null)
  exp_iso=$(printf '%s' "$resp" | jq -r '.expiresAt // empty' 2>/dev/null)
  [ -n "$tok" ] || return 1

  exp=$(parse_expiry "$exp_iso")
  (
    umask 077
    printf '%s\n%s\n' "$exp" "$tok" >"$CACHE"
  )
  printf '%s' "$tok"
}

repo_url() {
  if [ -n "${REPO_URL:-}" ]; then
    printf '%s' "$REPO_URL"
    return 0
  fi

  git -C "$WORKSPACE_ROOT" remote get-url origin 2>/dev/null ||
    git remote get-url origin 2>/dev/null ||
    return 1
}

if [ -n "${GH_TOKEN:-}" ]; then
  printf '%s' "$GH_TOKEN"
  exit 0
fi

if [ -n "${GITHUB_PERSONAL_ACCESS_TOKEN:-}" ]; then
  printf '%s' "$GITHUB_PERSONAL_ACCESS_TOKEN"
  exit 0
fi

cached_token || mint_token
