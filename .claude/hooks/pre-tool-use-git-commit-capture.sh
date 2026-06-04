#!/usr/bin/env bash
# Claude PreToolUse hook for Bash. Capture deliberate git commit
# messages as decision notes. Silent on KB failure.

set -u

[ "${KB_AUTO_MCP_DISABLED:-0}" = 1 ] && exit 0
[ -z "${KB_BEARER_TOKEN:-}" ] && exit 0

KB_URL="${KB_URL:-https://kb.jorisjonkers.dev}"
case "${KB_URL}" in
  */mcp) KB_MCP_URL="${KB_URL}" ;;
  *) KB_MCP_URL="${KB_URL%/}/mcp" ;;
esac

input="$(cat 2>/dev/null || true)"
read -r tool command < <(printf '%s' "${input}" | python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
    print(data.get("tool_name") or data.get("tool") or "", (data.get("tool_input") or {}).get("command") or "")
except Exception:
    print("")
' 2>/dev/null)
[ "${tool}" = "Bash" ] || exit 0

case "${command}" in
  *"git commit"*"-m"*) : ;;
  *) exit 0 ;;
esac

case "${command}" in
  *"fixup!"*|*"WIP"*|*"wip"*|*"Merge "*) exit 0 ;;
esac

title="$(printf '%s' "${command}" | python3 -c '
import re
import sys

cmd = sys.stdin.read()
match = re.search(r"-m\s+(\x27([^\x27]+)\x27|\"([^\"]+)\")", cmd)
if not match:
    sys.exit(0)
print(match.group(2) or match.group(3) or "", end="")
' 2>/dev/null)"
[ -z "${title}" ] && exit 0

project="$(git remote get-url origin 2>/dev/null | sed -e 's#\.git$##' -e 's#.*[/:]##')"
[ -n "${project}" ] || project="$(basename "$(git rev-parse --show-toplevel 2>/dev/null || pwd)")"
scope="project:${project}"

body="$(cat <<BODY
Commit message: ${title}

Captured automatically by the Claude PreToolUse git commit hook. The
diff and surrounding context live in git history.
BODY
)"

payload="$(python3 -c 'import json,sys
print(json.dumps({
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "knowledge.capture_decision",
    "arguments": {
      "title": sys.argv[1],
      "body": sys.argv[2],
      "scope": sys.argv[3],
      "source": "claude-code:auto-capture:git-commit",
      "tags": ["auto-capture", "git-commit"],
    },
  },
}))' "${title}" "${body}" "${scope}")"

curl -sS --connect-timeout 3 --max-time 5 \
  -H "Authorization: Bearer ${KB_BEARER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${payload}" \
  "${KB_MCP_URL}" >/dev/null 2>&1 || true
