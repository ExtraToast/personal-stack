#!/usr/bin/env bash
# UserPromptSubmit hook — calls knowledge.recall with the user's
# prompt content before the agent sees it. A tiny bounded hit list is
# injected so the agent has prior captures in hand without a KB dump.
#
# Silent on failure; the KB being unreachable should not block
# typing into Claude Code.

set -u

[ "${KB_AUTO_MCP_DISABLED:-0}" = 1 ] && exit 0
if [ -z "${KB_BEARER_TOKEN:-}" ]; then exit 0; fi

KB_URL="${KB_URL:-@KB_URL@}"
case "${KB_URL}" in
  */mcp) KB_MCP_URL="${KB_URL}" ;;
  *) KB_MCP_URL="${KB_URL%/}/mcp" ;;
esac

# Stdin carries the JSON event payload; the user_prompt field has the
# raw text. Use python (always present on macOS / NixOS) to extract.
prompt="$(python3 -c 'import json,sys; data=json.load(sys.stdin); print(data.get("user_prompt") or data.get("prompt") or "", end="")' 2>/dev/null || true)"

# Skip trivially-short prompts — overhead > value.
[ "${#prompt}" -lt "${KB_RECALL_MIN_PROMPT_CHARS:-40}" ] && exit 0

mode="${KB_RECALL_HOOK_MODE:-hybrid}"
limit="${KB_RECALL_HOOK_LIMIT:-3}"

recall_payload() {
  python3 -c 'import json,sys; print(json.dumps({
  "jsonrpc":"2.0","id":1,"method":"tools/call","params":{
    "name":"knowledge.recall",
    "arguments":{"query":sys.argv[1],"limit":int(sys.argv[2]),"mode":sys.argv[3]}}}))' \
    "$1" "$2" "$3"
}

call_recall() {
  payload=$(recall_payload "$1" "$2" "$3") || return 1
  curl -sS --connect-timeout 3 --max-time 5 \
    -H "Authorization: Bearer ${KB_BEARER_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${payload}" \
    "${KB_MCP_URL}" 2>/dev/null
}

response=$(call_recall "${prompt}" "${limit}" "${mode}") || response=""
if [ -z "${response}" ] && [ "${mode}" != "fast" ]; then
  response=$(call_recall "${prompt}" "${limit}" fast) || exit 0
fi
[ -n "${response}" ] || exit 0

hits=$(printf '%s' "${response}" | python3 -c 'import json,sys
try:
    data = json.load(sys.stdin)
    hits = data["result"]["structuredContent"]["hits"]
    if not hits: sys.exit(0)
    print("## Knowledge base — relevant prior captures")
    print()
    for h in hits:
        print(f"- **{h[\"title\"]}** (`{h[\"scope\"]}`, score {h[\"score\"]:.2f}) — id `{h[\"id\"]}`")
        snip = h.get("snippet","").replace("\n"," ").strip()
        if snip: print(f"  > {snip[:220]}")
except Exception:
    sys.exit(0)' 2>/dev/null) || exit 0

if [ -n "${hits}" ]; then
  printf '%s\n' "${hits}"
fi
