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

# Bootstrap git identity — assistant-api injects GIT_AUTHOR_NAME /
# GIT_AUTHOR_EMAIL when it creates the Pod; fall back to a clearly
# non-human identity so accidental commits are easy to spot.
git config --global user.name  "${GIT_AUTHOR_NAME:-Personal Stack Agent}"
git config --global user.email "${GIT_AUTHOR_EMAIL:-agents@jorisjonkers.dev}"
git config --global init.defaultBranch main

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

exec java \
  -XX:+UseZGC \
  -XX:MaxRAMPercentage=75 \
  -jar /opt/agent-gateway.jar
