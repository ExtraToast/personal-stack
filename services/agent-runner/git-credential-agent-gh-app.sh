#!/usr/bin/env bash
# Git credential helper for runner workspaces. Git calls this as
# `git credential-agent-gh-app get` and receives a repo-scoped GitHub
# App token as an HTTPS password, allowing `git push` without SSH keys.
set -u

TOKEN_HELPER="${AGENT_GITHUB_TOKEN_HELPER:-/usr/local/bin/agent-github-token}"

normalize_repo_path() {
  local url="$1"
  case "$url" in
    git@github.com:*) url="${url#git@github.com:}" ;;
    ssh://git@github.com/*) url="${url#ssh://git@github.com/}" ;;
    https://github.com/*) url="${url#https://github.com/}" ;;
    http://github.com/*) url="${url#http://github.com/}" ;;
    *) return 1 ;;
  esac
  url="${url%.git}"
  printf '%s' "$url"
}

action="${1:-}"
[ "$action" = "get" ] || exit 0

protocol=""
host=""
path=""
while IFS='=' read -r key value; do
  [ -n "$key" ] || break
  case "$key" in
    protocol) protocol="$value" ;;
    host) host="$value" ;;
    path) path="$value" ;;
  esac
done

[ "$protocol" = "https" ] || exit 0
[ "$host" = "github.com" ] || exit 0

repo_path=""
if [ -n "${REPO_URL:-}" ]; then
  repo_path=$(normalize_repo_path "$REPO_URL" || true)
fi

if [ -n "$path" ] && [ -n "$repo_path" ]; then
  path="${path%.git}"
  [ "$path" = "$repo_path" ] || exit 0
fi

token=$("$TOKEN_HELPER" 2>/dev/null) || exit 0
[ -n "$token" ] || exit 0

printf 'username=x-access-token\n'
printf 'password=%s\n\n' "$token"
