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
