#!/usr/bin/env bash

# Full old-stack backup runbook.
# You can either:
# 1. run this file line by line, or
# 2. execute it as a script after reviewing the commands.

set -euo pipefail

# Move into the repo root first.
cd /Users/j.w.jonkers/IDEAProjects/personal-stack-2

# Define one backup output directory for this entire run.
export RUN_DIR="$PWD/backups/run-$(date -u +%Y%m%dT%H%M%SZ)"

# Cloud/VPS backup connection settings.
export BACKUP_CLOUD_SSH_HOST=100.64.0.1
export BACKUP_CLOUD_SSH_USER=deploy
export BACKUP_CLOUD_SSH_PORT=2222
export BACKUP_CLOUD_SSH_IDENTITY_FILE="$HOME/.ssh/ps-vps-1"

# Home/GTX960M backup connection settings.
export BACKUP_HOME_SSH_HOST=100.64.0.2
export BACKUP_HOME_SSH_USER=extratoast
export BACKUP_HOME_SSH_PORT=22
export BACKUP_HOME_SSH_IDENTITY_FILE="$HOME/.ssh/ps-gtx960m"

# Force both backup scripts to write into the same run directory.
export BACKUP_OUTPUT_DIR="$RUN_DIR"

# Convenience SSH wrappers so the remote commands stay readable.
vps() {
  ssh -i "$HOME/.ssh/ps-vps-1" -p 2222 deploy@100.64.0.1 "$@"
}

homehost() {
  ssh -i "$HOME/.ssh/ps-gtx960m" -p 22 extratoast@100.64.0.2 "$@"
}

# Check that the manifest still covers every declared Nomad host volume.
infra/scripts/audit-backup-scope.sh

# Check passwordless sudo on both source hosts.
vps 'sudo -n true'
homehost 'sudo -n true'

# Check that the current Vault token on the VPS still works.
vps 'sudo bash -s' <<'EOF'
source /opt/personal-stack/.vault-keys
export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN="$VAULT_ROOT_TOKEN"
vault status >/dev/null
echo VAULT_OK
EOF

# Check that RabbitMQ credentials can be resolved from Vault and authenticate locally.
vps 'sudo bash -s' <<'EOF'
source /opt/personal-stack/.vault-keys
export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN="$VAULT_ROOT_TOKEN"
rmq_user="$(vault kv get -field=rabbitmq.user secret/platform/rabbitmq)"
rmq_password="$(vault kv get -field=rabbitmq.password secret/platform/rabbitmq)"
curl -fsS --user "$rmq_user:$rmq_password" http://127.0.0.1:15672/api/overview >/dev/null
echo RABBITMQ_OK
EOF

# Check that the current Nomad management token on the VPS still works.
vps 'sudo bash -s' <<'EOF'
source /opt/personal-stack/.nomad-keys
export NOMAD_ADDR=http://127.0.0.1:4646 NOMAD_TOKEN="$NOMAD_BOOTSTRAP_TOKEN"
nomad status >/dev/null
echo NOMAD_OK
EOF

# Capture live service-native snapshots before stopping anything.
infra/scripts/backup-service-snapshots.sh

# Stop all Nomad jobs on the VPS before stopping Nomad itself.
vps 'sudo bash -s' <<'EOF'
source /opt/personal-stack/.nomad-keys
export NOMAD_ADDR=http://127.0.0.1:4646 NOMAD_TOKEN="$NOMAD_BOOTSTRAP_TOKEN"
nomad job status -json | jq -r '.[].ID' | while read -r job; do
  nomad job stop -purge "$job"
done
EOF

# Stop remaining stateful host services on the VPS.
vps 'sudo systemctl stop vault nomad consul'

# Stop stateful host services on the home node.
homehost 'sudo systemctl stop nomad consul smbd adguard-home'

# Pull filesystem backups from both hosts.
infra/scripts/backup-service-state.sh

# Verify the run metadata, required artifacts, and recorded checksums.
infra/scripts/verify-backup-run.sh "$RUN_DIR"

# Verify every compressed filesystem archive is readable.
awk -F '\t' 'NR > 1 && $5 == "backed-up" { print $4 }' "$RUN_DIR/archives.tsv" | while read -r archive; do
  gzip -t "$archive"
done

# Optional: inspect the resulting snapshot/export table.
column -t -s $'\t' "$RUN_DIR/service-snapshots.tsv"

# Optional: inspect the resulting filesystem-archive table.
column -t -s $'\t' "$RUN_DIR/archives.tsv"

# Optional: print the run directory so it is easy to find afterwards.
printf 'Backup run saved in %s\n' "$RUN_DIR"
