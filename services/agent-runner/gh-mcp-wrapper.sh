#!/usr/bin/env bash
# Launches the GitHub MCP server (stdio) with a fresh GitHub App
# installation token. The App token rotates hourly, which a static MCP
# auth header cannot follow, so the token is minted at spawn (MCP
# servers are started once per agent session) using the same endpoint +
# on-disk cache as the gh CLI wrapper — back-to-back spawns and gh calls
# share one token. A session running past the token's ~1h life needs the
# MCP server restarted to re-mint.
#
# Degrades gracefully: when the App is not wired (no endpoint/bearer/repo
# in the env) the server still starts, just unauthenticated, rather than
# failing the whole MCP connection.
set -u

CACHE="${GH_APP_TOKEN_CACHE:-/tmp/.gh-app-token}"
# Re-mint when the cached token has less than this many seconds left.
SKEW=300

mint_token() {
  [ -n "${GITHUB_APP_TOKEN_URL:-}" ] && [ -n "${GITHUB_APP_TOKEN_BEARER:-}" ] && [ -n "${REPO_URL:-}" ] || return 1

  local now exp tok resp exp_iso
  now=$(date +%s)
  if [ -f "$CACHE" ]; then
    exp=$(sed -n 1p "$CACHE" 2>/dev/null)
    tok=$(sed -n 2p "$CACHE" 2>/dev/null)
    if [ -n "$exp" ] && [ -n "$tok" ] && [ "$exp" -gt "$((now + SKEW))" ] 2>/dev/null; then
      printf '%s' "$tok"
      return 0
    fi
  fi

  resp=$(curl -fsS --max-time 10 -X POST "$GITHUB_APP_TOKEN_URL" \
    -H "Authorization: Bearer $GITHUB_APP_TOKEN_BEARER" \
    -H "Content-Type: application/json" \
    -d "{\"repoUrl\":\"$REPO_URL\"}" 2>/dev/null) || return 1

  tok=$(printf '%s' "$resp" | jq -r '.token // empty' 2>/dev/null)
  exp_iso=$(printf '%s' "$resp" | jq -r '.expiresAt // empty' 2>/dev/null)
  [ -n "$tok" ] || return 1

  exp=$(date -d "$exp_iso" +%s 2>/dev/null || echo 0)
  (
    umask 077
    printf '%s\n%s\n' "$exp" "$tok" >"$CACHE"
  )
  printf '%s' "$tok"
}

TOKEN=$(mint_token || true)
[ -n "$TOKEN" ] && export GITHUB_PERSONAL_ACCESS_TOKEN="$TOKEN"

exec github-mcp-server stdio
