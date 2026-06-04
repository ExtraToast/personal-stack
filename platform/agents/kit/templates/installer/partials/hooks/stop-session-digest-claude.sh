#!/usr/bin/env bash
# Stop hook: Reflexion-style session-digest auto-capture.
#
# Reads the transcript path from the hook input, asks
# `knowledge.digest_transcript` for candidate captures, applies the
# client-side policy (confidence floor, per-session token bucket,
# cross-session dedupe via `knowledge.recall`), and emits survivors
# via `knowledge.capture_lesson` with a distinct `source` so the
# operator can bulk-revoke if needed.
#
# Async by default (Claude Code's settings.json marks Stop hooks
# `async: true`), so blocking is fine here. Still bounded at ~60s.

set -u

[ "${KB_AUTO_MCP_DISABLED:-0}" = 1 ] && exit 0
[ -z "${KB_BEARER_TOKEN:-}" ] && exit 0

KB_URL="${KB_URL:-@KB_URL@}"
case "${KB_URL}" in
  */mcp) KB_MCP_URL="${KB_URL}" ;;
  *) KB_MCP_URL="${KB_URL%/}/mcp" ;;
esac
STATE_DIR="${HOME}/.claude/state"
LOG="${STATE_DIR}/auto-mcp.log"
mkdir -p "${STATE_DIR}"

input=$(cat 2>/dev/null || true)
read -r session transcript_path < <(printf '%s' "${input}" | python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
    print(data.get("session_id") or "unknown", data.get("transcript_path") or "")
except Exception:
    print("unknown")' 2>/dev/null)

# Per-session token bucket: max 5 auto-captures per session by default.
session_dir="${STATE_DIR}/sessions/${session}"
mkdir -p "${session_dir}"
remaining_file="${session_dir}/digest-budget"
if [ -e "${remaining_file}" ]; then
  remaining=$(cat "${remaining_file}")
else
  remaining="${KB_DIGEST_MAX_CAPTURES:-5}"
fi

# Load the transcript text. Claude Code stores it as JSONL; the
# digest tool tolerates raw text, so a stringify-then-send is fine.
if [ -z "${transcript_path}" ] || [ ! -r "${transcript_path}" ]; then
  echo "$(date -u +%FT%TZ) stop-digest skip: no transcript" >>"${LOG}" 2>/dev/null
  exit 0
fi
transcript=$(python3 -c '
import json, os, sys
out = []
max_chars = int(os.environ.get("KB_DIGEST_MAX_CHARS", "60000"))
with open(sys.argv[1]) as f:
    for line in f:
        line = line.strip()
        if not line: continue
        try:
            row = json.loads(line)
            role = row.get("role") or row.get("type") or "?"
            content = row.get("content") or row.get("text") or ""
            if isinstance(content, list):
                content = " ".join(p.get("text","") for p in content if isinstance(p, dict))
            out.append(f"[{role}] {content}")
        except Exception:
            pass
joined = "\n".join(out)
print(joined[-max_chars:])' "${transcript_path}" 2>/dev/null) || exit 0

[ -z "${transcript}" ] && exit 0

# Server-side digest call.
payload=$(python3 -c 'import json,sys; print(json.dumps({
  "jsonrpc":"2.0","id":1,"method":"tools/call","params":{
    "name":"knowledge.digest_transcript",
    "arguments":{"transcript":sys.argv[1],"max_candidates":int(sys.argv[2])}}}))' \
  "${transcript}" "${remaining}")

response=$(curl -sS --connect-timeout 5 --max-time 60 \
  -H "Authorization: Bearer ${KB_BEARER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${payload}" \
  "${KB_MCP_URL}" 2>/dev/null) || exit 0

candidates=$(printf '%s' "${response}" | python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
    print(json.dumps(data["result"]["structuredContent"]["candidates"]))
except Exception:
    print("[]")' 2>/dev/null || echo "[]")

# Emit each survivor via capture_lesson. Server-side digest already
# applied the confidence floor; we re-apply the per-session token
# bucket here, and the source tagging.
emitted=0
while IFS= read -r line; do
  [ -z "${line}" ] && continue
  [ "${remaining}" -le 0 ] && break
  title=$(printf '%s' "${line}" | python3 -c 'import json,sys; print(json.loads(sys.stdin.read())["title"], end="")')
  body=$(printf '%s' "${line}" | python3 -c 'import json,sys; print(json.loads(sys.stdin.read())["body"], end="")')
  topic=$(printf '%s' "${line}" | python3 -c 'import json,sys; print((json.loads(sys.stdin.read()).get("suggested_topic") or ""), end="")')
  tags_json=$(printf '%s' "${line}" | python3 -c 'import json,sys; print(json.dumps(json.loads(sys.stdin.read()).get("suggested_tags") or []), end="")')
  [ -n "${title}" ] && [ -n "${body}" ] || continue

  dedupe_payload=$(python3 -c 'import json,sys; print(json.dumps({
    "jsonrpc":"2.0","id":1,"method":"tools/call","params":{
      "name":"knowledge.recall","arguments":{
        "query": sys.argv[1], "limit": 1, "mode": "hybrid"}}}))' "${title} ${body}")
  duplicate_count=$(curl -sS --connect-timeout 3 --max-time 5 \
    -H "Authorization: Bearer ${KB_BEARER_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${dedupe_payload}" \
    "${KB_MCP_URL}" 2>/dev/null | python3 -c '
import json, sys
try:
    hits = json.load(sys.stdin)["result"]["structuredContent"]["hits"]
    print(1 if hits and float(hits[0].get("score", 0)) >= float("'${KB_DIGEST_DEDUPE_SCORE:-0.86}'") else 0)
except Exception:
    print(0)
' 2>/dev/null || echo 0)
  [ "${duplicate_count}" = 1 ] && continue

  scope_arg=""
  [ -n "${topic}" ] && scope_arg="topic:${topic}"
  capture_payload=$(python3 -c 'import json,sys; print(json.dumps({
    "jsonrpc":"2.0","id":1,"method":"tools/call","params":{
      "name":"knowledge.capture_lesson","arguments":{
        "title": sys.argv[1],
        "body": sys.argv[2],
        "scope": sys.argv[3] or None,
        "source": "claude-code:auto-digest:" + sys.argv[4],
        "tags": json.loads(sys.argv[5])
      }}}))' "${title}" "${body}" "${scope_arg}" "${session}" "${tags_json}")
  curl -sS --connect-timeout 3 --max-time 10 \
    -H "Authorization: Bearer ${KB_BEARER_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${capture_payload}" \
    "${KB_MCP_URL}" >/dev/null 2>&1 || continue
  remaining=$((remaining - 1))
  emitted=$((emitted + 1))
  echo "$(date -u +%FT%TZ) stop-digest emit session=${session} title=${title}" >>"${LOG}" 2>/dev/null
done < <(printf '%s' "${candidates}" | python3 -c '
import json, sys
try:
    rows = json.load(sys.stdin)
    for r in rows: print(json.dumps(r))
except Exception:
    pass' 2>/dev/null)

echo "${remaining}" > "${remaining_file}"
echo "$(date -u +%FT%TZ) stop-digest done session=${session} emitted=${emitted}" >>"${LOG}" 2>/dev/null
