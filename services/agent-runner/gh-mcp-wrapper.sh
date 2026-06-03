#!/usr/bin/env bash
# Launches the GitHub MCP server (stdio) with a GitHub token.
#
# github-mcp-server requires GITHUB_PERSONAL_ACCESS_TOKEN at process
# start. The shared token helper returns a pre-seeded token or mints a
# fresh GitHub App installation token through assistant-api using the
# same cache as the gh CLI wrapper.
set -u

TOKEN_HELPER="${AGENT_GITHUB_TOKEN_HELPER:-/usr/local/bin/agent-github-token}"
TOKEN=$("$TOKEN_HELPER" 2>/dev/null || true)

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
