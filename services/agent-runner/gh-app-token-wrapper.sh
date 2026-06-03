#!/usr/bin/env bash
# `gh` wrapper for agent runners. Before delegating to the real gh, it
# fetches a fresh, repo-scoped GitHub App installation token from
# assistant-api (scoped to PR comments + Actions re-runs only) and
# exports it as GH_TOKEN. The token is cached on disk and reused while
# it still has spare life, so back-to-back gh calls mint at most once
# per token lifetime rather than on every invocation.
#
# Pure passthrough — `gh` still runs — when the App is not wired (no
# endpoint URL / bearer / repo in the environment) or when minting
# fails, so a misconfiguration degrades to unauthenticated gh rather
# than a hard error.
set -u

REAL_GH=/usr/bin/gh
CACHE="${GH_APP_TOKEN_CACHE:-/tmp/.gh-app-token}"
# Re-mint when the cached token has less than this many seconds left.
SKEW=300

refresh_token() {
  [ -n "${GITHUB_APP_TOKEN_URL:-}" ] && [ -n "${GITHUB_APP_TOKEN_BEARER:-}" ] && [ -n "${REPO_URL:-}" ] || return 1

  local now exp tok resp exp_iso
  now=$(date +%s)
  if [ -f "$CACHE" ]; then
    exp=$(sed -n 1p "$CACHE" 2>/dev/null)
    tok=$(sed -n 2p "$CACHE" 2>/dev/null)
    if [ -n "$exp" ] && [ -n "$tok" ] && [ "$exp" -gt "$((now + SKEW))" ] 2>/dev/null; then
      export GH_TOKEN="$tok"
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
  export GH_TOKEN="$tok"
}

if [ -n "${GITHUB_PERSONAL_ACCESS_TOKEN:-}" ] && [ -z "${GH_TOKEN:-}" ]; then
  export GH_TOKEN="$GITHUB_PERSONAL_ACCESS_TOKEN"
fi

refresh_token || true
exec "$REAL_GH" "$@"
