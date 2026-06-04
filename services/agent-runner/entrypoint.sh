#!/bin/sh
# agent-runner entrypoint. Three responsibilities:
#
#   1. Surface the deploy-key mount through to a writable file with
#      0600 so ssh accepts it. The Vault Secret mount makes the file
#      world-readable; openssh refuses keys that aren't 0600.
#   2. Make the runner's identity reproducible (git user.name +
#      user.email come from env or fall back to a marker).
#   3. exec the gateway jar as PID 1 (via tini so children reap).
set -eu

CODEX_HOME="${CODEX_HOME:-$HOME/.codex}"
CLAUDE_CONFIG_DIR="${CLAUDE_CONFIG_DIR:-$HOME/.claude}"

check_agent_kit_manifest() {
  agent_name="$1"
  manifest_path="$2"
  expected_version="${AGENT_KIT_EXPECTED_VERSION:-}"

  if [ ! -f "$manifest_path" ]; then
    echo "[entrypoint] WARN: knowledge-system agent kit manifest missing for ${agent_name} at ${manifest_path}; run agents-kb-install or /install.sh --agent all"
    return
  fi

  installed_version=$(awk -F= '/^version=/{print $2; exit}' "$manifest_path" 2>/dev/null || true)
  if [ -z "$installed_version" ]; then
    echo "[entrypoint] WARN: knowledge-system agent kit manifest for ${agent_name} has no version at ${manifest_path}"
    return
  fi

  if [ -n "$expected_version" ] && [ "$installed_version" != "$expected_version" ]; then
    echo "[entrypoint] WARN: knowledge-system agent kit manifest for ${agent_name} is ${installed_version}, expected ${expected_version}; run agents-kb-install or /install.sh --agent all"
  fi
}

check_agent_kit_manifests() {
  check_agent_kit_manifest "Claude" "${CLAUDE_CONFIG_DIR}/.knowledge-system-version"
  check_agent_kit_manifest "Codex" "${CODEX_HOME}/.knowledge-system-version"
}

if [ "${AGENT_RUNNER_ENTRYPOINT_SELF_TEST:-}" = "agent-kit-manifest" ]; then
  check_agent_kit_manifests
  exit 0
fi

check_agent_kit_manifests

# Bootstrap git identity — assistant-api injects GIT_AUTHOR_NAME /
# GIT_AUTHOR_EMAIL when it creates the Pod; fall back to a clearly
# non-human identity so accidental commits are easy to spot.
git config --global user.name  "${GIT_AUTHOR_NAME:-Personal Stack Agent}"
git config --global user.email "${GIT_AUTHOR_EMAIL:-agents@jorisjonkers.dev}"
git config --global init.defaultBranch main
git config --global credential.helper agent-gh-app
git config --global credential.useHttpPath true
export GIT_TERMINAL_PROMPT=0

# Restore ~/.claude.json from the latest PVC-backed backup if the
# runtime file is missing. Claude Code keeps OAuth tokens in
# ~/.claude/.credentials.json (lives on claude-credentials PVC, so
# survives Pod restarts) but its user config — including the
# org/project bindings without which `claude -p` refuses to run —
# sits at ~/.claude.json (sibling of ~/.claude/, in the container's
# writable layer, lost on every restart). Claude itself writes a
# rolling backup to ~/.claude/backups/.claude.json.backup.<ts> on
# every config write, which DOES land on the PVC, so the post-
# bootstrap behaviour is "every fresh Pod has a backup it can
# restore from". This snippet performs that restore. The latest
# backup wins so token-refresh updates propagate forward.
if [ ! -f "$HOME/.claude.json" ] && [ -d "$HOME/.claude/backups" ]; then
  latest=$(ls -1t "$HOME/.claude/backups"/.claude.json.backup.* 2>/dev/null | head -n1 || true)
  if [ -n "$latest" ]; then
    cp "$latest" "$HOME/.claude.json"
  fi
fi

# Suppress Claude's first-run prompts without clobbering a restored
# config. The OAuth token persists on the claude-credentials PVC, but
# the per-user flags that record "theme chosen / onboarding done /
# directory trusted" live in ~/.claude.json, which is lost on every
# fresh Pod unless a backup happened to exist. A logged-in CLI that
# still has theme==undefined or hasCompletedOnboarding!=true re-runs
# the onboarding wizard (which is also where the theme picker lives),
# and an untrusted project dir re-shows the trust dialog — both block
# the non-interactive tmux session. `jq //=` fills only the missing
# keys, so a real restored config (its own theme, oauthAccount, the
# project history array) is preserved verbatim. WORKSPACE_ROOT keys
# the per-project trust entry to the dir the gateway launches the CLI
# in (AgentSessionManager defaults cwd to the gateway's workspace-root).
WORKSPACE_ROOT="${WORKSPACE_ROOT:-/workspace}"
if ! git config --global --get-all safe.directory | grep -Fxq "$WORKSPACE_ROOT"; then
  git config --global --add safe.directory "$WORKSPACE_ROOT"
fi
if [ ! -f "$HOME/.claude.json" ]; then
  echo '{}' > "$HOME/.claude.json"
fi
claude_tmp=$(mktemp)
if jq --arg ws "$WORKSPACE_ROOT" '
      (.theme //= "dark")
      | (.hasCompletedOnboarding //= true)
      | (.bypassPermissionsModeAccepted //= true)
      | (.projects //= {})
      | (.projects[$ws] //= {})
      | (.projects[$ws].hasTrustDialogAccepted //= true)
      | (.projects[$ws].hasCompletedProjectOnboarding //= true)
    ' "$HOME/.claude.json" > "$claude_tmp"; then
  mv "$claude_tmp" "$HOME/.claude.json"
else
  rm -f "$claude_tmp"
fi

# Register MCP servers into Claude Code from the declarative ConfigMap
# (agents-mcp-servers, mounted at /etc/agent-mcp). The selected profile
# is an mcpServers object with @KB_URL@/@KB_BEARER_TOKEN@ placeholders
# filled from the Pod env, so no secret is baked into the ConfigMap.
# The managed servers win for their own keys; any hand-added server
# already in the config is preserved. Absent mount (feature off) => no-op.
AGENT_MCP_PROFILE="${AGENT_MCP_PROFILE:-minimal}"
case "$AGENT_MCP_PROFILE" in
  minimal|frontend|cluster|code-intel|full-diagnostic) ;;
  *)
    echo "[entrypoint] WARN: unknown AGENT_MCP_PROFILE=$AGENT_MCP_PROFILE; using minimal"
    AGENT_MCP_PROFILE="minimal"
    ;;
esac

AGENT_MCP_DIR="${AGENT_MCP_DIR:-/etc/agent-mcp}"
if [ -n "${AGENT_MCP_SERVERS_FILE:-}" ]; then
  MCP_SERVERS_FILE="$AGENT_MCP_SERVERS_FILE"
else
  MCP_PROFILE_FILE="${AGENT_MCP_DIR}/claude-mcp-servers.${AGENT_MCP_PROFILE}.json"
  MCP_MINIMAL_FILE="${AGENT_MCP_DIR}/claude-mcp-servers.minimal.json"
  MCP_LEGACY_FILE="${AGENT_MCP_DIR}/claude-mcp-servers.json"
  if [ -f "$MCP_PROFILE_FILE" ]; then
    MCP_SERVERS_FILE="$MCP_PROFILE_FILE"
  elif [ -f "$MCP_MINIMAL_FILE" ]; then
    echo "[entrypoint] WARN: MCP profile $AGENT_MCP_PROFILE not found; using minimal"
    MCP_SERVERS_FILE="$MCP_MINIMAL_FILE"
  else
    MCP_SERVERS_FILE="$MCP_LEGACY_FILE"
  fi
fi
if [ -f "$MCP_SERVERS_FILE" ]; then
  mcp_rendered=$(mktemp)
  sed -e "s|@KB_URL@|${KB_URL:-}|g" \
      -e "s|@KB_BEARER_TOKEN@|${KB_BEARER_TOKEN:-}|g" \
      "$MCP_SERVERS_FILE" > "$mcp_rendered"
  mcp_merged=$(mktemp)
  if jq -s '
        .[0] as $cfg | .[1] as $servers
        | $cfg + { mcpServers: (($cfg.mcpServers // {}) * $servers) }
      ' "$HOME/.claude.json" "$mcp_rendered" > "$mcp_merged" 2>/dev/null; then
    mv "$mcp_merged" "$HOME/.claude.json"
  else
    echo "[entrypoint] WARN: failed to merge MCP servers from $MCP_SERVERS_FILE"
    rm -f "$mcp_merged"
  fi
  rm -f "$mcp_rendered"
fi

# Trust the workspace for Codex the same way. ~/.codex sits on the
# codex-credentials PVC (CODEX_HOME), so auth.json persists, but a
# fresh checkout of the workspace dir is "untrusted" until the
# interactive trust prompt is answered, and the default approval
# policy stops to ask before each command — both stall the tmux
# session. Seeding global non-interactive approval/sandbox plus a
# per-project trusted entry removes every prompt. Only created when
# absent so a hand-edited config on the PVC is never overwritten.
if [ ! -f "$CODEX_HOME/config.toml" ]; then
  mkdir -p "$CODEX_HOME"
  cat > "$CODEX_HOME/config.toml" <<EOF
approval_policy = "never"
sandbox_mode = "danger-full-access"

[projects."$WORKSPACE_ROOT"]
trust_level = "trusted"
EOF
fi

# Codex managed config: force ChatGPT Apps/connectors off and re-seed the
# MCP server set on EVERY boot. Codex exposes account-level connectors as
# `codex_apps.*` tools; the GitHub connector among them is bound to the
# ChatGPT account's own GitHub OAuth identity and therefore inherits every
# org that identity can see (employer orgs included), completely bypassing
# the repo-scoped GitHub App installation token. The only sanctioned GitHub
# access for an agent is the `github` MCP server (gh-mcp-wrapper → a short-
# lived, repo-scoped App token). `features.apps = false` gates the whole
# experimental connectors subsystem off; `apps._default.enabled = false` is
# defence-in-depth (per-app disables are ignored upstream — openai/codex
# #17588), so removing the connector on the ChatGPT account (see SETUP.md)
# stays the hard guarantee. Managed each boot so the invariant survives a
# hand-edited PVC config; the create-once approval/sandbox/trust config and
# any unrelated [features] dotted keys are preserved.
#
# Codex reads remote HTTP MCP servers natively and takes the bearer at
# request time from KB_BEARER_TOKEN (bearer_token_env_var), so no secret
# lands in config.toml — only @KB_URL@ is substituted.
if [ -n "${AGENT_CODEX_MCP_FILE:-}" ]; then
  CODEX_MCP_FILE="$AGENT_CODEX_MCP_FILE"
else
  CODEX_PROFILE_FILE="${AGENT_MCP_DIR}/codex-mcp-servers.${AGENT_MCP_PROFILE}.toml"
  CODEX_MINIMAL_FILE="${AGENT_MCP_DIR}/codex-mcp-servers.minimal.toml"
  CODEX_LEGACY_FILE="${AGENT_MCP_DIR}/codex-mcp-servers.toml"
  if [ -f "$CODEX_PROFILE_FILE" ]; then
    CODEX_MCP_FILE="$CODEX_PROFILE_FILE"
  elif [ -f "$CODEX_MINIMAL_FILE" ]; then
    echo "[entrypoint] WARN: Codex MCP profile $AGENT_MCP_PROFILE not found; using minimal"
    CODEX_MCP_FILE="$CODEX_MINIMAL_FILE"
  else
    CODEX_MCP_FILE="$CODEX_LEGACY_FILE"
  fi
fi
if [ -f "$CODEX_HOME/config.toml" ]; then
  codex_tmp=$(mktemp)
  # Strip every managed section so re-applying never duplicates a key: the
  # [features]/[apps*] disable tables, any dotted features.apps / apps._default
  # keys, and the [mcp_servers.*] tables. Other top-level tables (projects)
  # and keys are passed through untouched.
  awk '
    /^\[mcp_servers\./ { skip = 1; next }
    /^\[features\]/    { skip = 1; next }
    /^\[apps\]/        { skip = 1; next }
    /^\[apps\./        { skip = 1; next }
    /^\[/              { skip = 0 }
    skip               { next }
    /^[[:space:]]*features\.apps[[:space:]]*=/ { next }
    /^[[:space:]]*apps\._default\./            { next }
    { print }
  ' "$CODEX_HOME/config.toml" > "$codex_tmp"
  {
    echo ""
    echo "[features]"
    echo "apps = false"
    echo ""
    echo "[apps._default]"
    echo "enabled = false"
    if [ -f "$CODEX_MCP_FILE" ]; then
      echo ""
      sed -e "s|@KB_URL@|${KB_URL:-}|g" "$CODEX_MCP_FILE"
    fi
  } >> "$codex_tmp"
  # Collapse runs of blank lines so repeated boots don't grow the file.
  awk 'NF{print; blank=0; next} {blank++} blank<2' "$codex_tmp" > "$CODEX_HOME/config.toml"
  rm -f "$codex_tmp"
fi

# Stage the deploy key into /tmp with restrictive perms. The gateway
# does the same dance defensively, but doing it once at boot makes
# manual debugging from the shell less surprising.
if [ -f /var/run/secrets/agents/github-deploy-key/private_key ]; then
  cp /var/run/secrets/agents/github-deploy-key/private_key /tmp/agent-deploy-key
  chmod 0600 /tmp/agent-deploy-key
  if [ -f /var/run/secrets/agents/github-deploy-key/known_hosts ]; then
    mkdir -p "$HOME/.ssh"
    cp /var/run/secrets/agents/github-deploy-key/known_hosts "$HOME/.ssh/known_hosts"
  fi
fi

# Clone the repo into the workspace at boot. The orchestrator passes
# REPO_URL (+ optional REPO_BRANCH) for repo-backed workspaces. Cloning
# here — with the deploy key staged above — avoids the race that left
# repo-backed workspaces empty: the old create-time gateway.clone fired
# before this gateway was up and was lost. Idempotent — only clones into
# an empty workspace, so a Pod restart on a populated PVC never re-clones.
# Failure is non-fatal so the gateway still starts and the repo can be
# cloned by hand.
if [ -n "${REPO_URL:-}" ] && [ -z "$(ls -A "$WORKSPACE_ROOT" 2>/dev/null || true)" ]; then
  export GIT_SSH_COMMAND="ssh -i /tmp/agent-deploy-key -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=${HOME}/.ssh/known_hosts"
  if [ -n "${REPO_BRANCH:-}" ]; then
    git clone --branch "$REPO_BRANCH" "$REPO_URL" "$WORKSPACE_ROOT" || echo "[entrypoint] WARN: clone of $REPO_URL failed; starting gateway anyway"
  else
    git clone "$REPO_URL" "$WORKSPACE_ROOT" || echo "[entrypoint] WARN: clone of $REPO_URL failed; starting gateway anyway"
  fi
fi

# Keep boot-time clone on the deploy-key path above, then make every
# interactive git operation prefer HTTPS so the credential helper can
# provide the short-lived GitHub App token. This fixes pushes from
# repo-backed workspaces whose SSH deploy key is absent or read-only,
# and it also covers `gh pr create` when gh shells out to git.
git config --global url.https://github.com/.insteadOf git@github.com:
git config --global url.https://github.com/.insteadOf ssh://git@github.com/

# The gateway shares this Pod's memory cgroup with the agent CLIs it
# launches (Claude Code, Codex) and whatever the workspace itself runs.
# MaxRAMPercentage made the JVM lazily fill 75% of the Pod limit with
# garbage before collecting, starving those co-tenants and getting the
# whole Pod OOMKilled. The gateway is a thin streaming relay with a tiny
# live set, so an absolute 1g heap is ample and leaves the rest of the
# Pod's RAM for the CLIs regardless of the limit.
exec java \
  -XX:+UseZGC \
  -Xmx1g \
  -jar /opt/agent-gateway.jar
