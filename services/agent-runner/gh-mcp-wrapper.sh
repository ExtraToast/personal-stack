#!/usr/bin/env bash
# Launches the GitHub MCP server (stdio) with a GitHub token.
#
# github-mcp-server requires GITHUB_PERSONAL_ACCESS_TOKEN at process
# start. The runner normally mints a fresh GitHub App installation token
# through assistant-api using the same cache as the gh CLI wrapper. A
# pre-seeded GITHUB_PERSONAL_ACCESS_TOKEN or GH_TOKEN still wins, which
# keeps local/dev runners usable without the in-cluster token endpoint.
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

TOKEN="${GITHUB_PERSONAL_ACCESS_TOKEN:-${GH_TOKEN:-}}"
if [ -z "$TOKEN" ]; then
  TOKEN=$(mint_token || true)
fi

if [ -z "$TOKEN" ]; then
  cat >&2 <<'EOF'
[gh-mcp-wrapper] ERROR: no GitHub token available for github-mcp-server.
[gh-mcp-wrapper] Set GITHUB_PERSONAL_ACCESS_TOKEN/GH_TOKEN, or provide
[gh-mcp-wrapper] GITHUB_APP_TOKEN_URL, GITHUB_APP_TOKEN_BEARER, and REPO_URL
[gh-mcp-wrapper] so the runner can mint an installation token.
EOF
  exit 78
fi

export GITHUB_PERSONAL_ACCESS_TOKEN="$TOKEN"

exec github-mcp-server stdio
