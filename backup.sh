#!/usr/bin/env bash

# Full old-stack backup runbook.
# You can either:
# 1. run this file line by line, or
# 2. execute it as a script after reviewing the commands.

set -euo pipefail
trap 'printf "backup.sh failed at line %s\n" "$LINENO" >&2' ERR
set -x

# Move into the repo root first.
cd /Users/j.w.jonkers/IDEAProjects/personal-stack-2

# Define one backup output directory per local calendar day so repeated reruns
# on the same day reuse the same folder instead of creating many timestamped
# runs. Override RUN_DIR manually if you want a separate run folder.
export RUN_DIR="${RUN_DIR:-$PWD/backups/run-$(date +%Y%m%d)}"

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

# Mirror all output to a daily log file while still printing to the terminal.
mkdir -p "$RUN_DIR"
exec > >(tee -a "$RUN_DIR/backup.log") 2>&1

# Convenience SSH wrappers so the remote commands stay readable.
vps() {
  ssh -i "$HOME/.ssh/ps-vps-1" -p 2222 deploy@100.64.0.1 "$@"
}

homehost() {
  ssh -i "$HOME/.ssh/ps-gtx960m" -p 22 extratoast@100.64.0.2 "$@"
}

# Step 0: restore the old environment so a fresh rerun can capture live state.
# This uses the VPS's own checked-out repo under /opt/personal-stack so the
# backup reflects the currently deployed old stack, not newer local changes.
vps 'sudo systemctl start consul vault nomad'
homehost 'sudo systemctl start consul nomad smbd adguard-home'

# Unseal Vault on the VPS before any Vault-backed checks or job restores.
vps 'sudo bash -s' <<'EOF'
set -euo pipefail
source /opt/personal-stack/.vault-keys
export VAULT_ADDR=http://127.0.0.1:8200

for _ in $(seq 1 30); do
  if vault status >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if vault status -format=json | jq -e '.sealed == true' >/dev/null; then
  vault operator unseal "$VAULT_UNSEAL_KEY" >/dev/null
fi

vault status >/dev/null
echo VAULT_UNSEALED
EOF

# Re-submit only the RabbitMQ job so the live definitions export can run again.
# Avoid touching PostgreSQL or the rest of the old stack during a backup rerun.
vps 'sudo bash -s' <<'EOF'
set -euo pipefail
source /opt/personal-stack/.nomad-keys
export NOMAD_ADDR=http://127.0.0.1:4646 NOMAD_TOKEN="$NOMAD_BOOTSTRAP_TOKEN"
cd /opt/personal-stack
nomad job run -detach infra/nomad/jobs/data/rabbitmq.nomad.hcl

source /opt/personal-stack/.vault-keys
export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN="$VAULT_ROOT_TOKEN"

for _ in $(seq 1 60); do
  rmq_user="$(vault kv get -field=rabbitmq.user secret/platform/rabbitmq)"
  rmq_password="$(vault kv get -field=rabbitmq.password secret/platform/rabbitmq)"
  if curl -fsS --user "$rmq_user:$rmq_password" http://127.0.0.1:15672/api/overview >/dev/null; then
    echo RABBITMQ_RESTORED
    exit 0
  fi
  sleep 3
done

nomad job status rabbitmq || true
exit 1
EOF

# Step 1: audit manifest coverage.
# Check that the manifest still covers every declared Nomad host volume.
# Note: the script will also print system-service paths such as /opt/consul,
# /opt/nomad, /opt/vault/data, /opt/adguard-home, and /var/lib/samba.
# Those are expected because they are backup paths, but not Nomad host_volume
# declarations. The failure condition is only missing declared host volumes.
infra/scripts/audit-backup-scope.sh

# Step 2: confirm remote sudo and control-plane auth.
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

# Step 3: capture live snapshots and exports while services are still up.
# Capture live service-native snapshots before stopping anything.
infra/scripts/backup-service-snapshots.sh

# Step 4: stop old workloads.
# Stop all Nomad jobs on the VPS before stopping Nomad itself.
vps 'sudo bash -s' <<'EOF'
source /opt/personal-stack/.nomad-keys
export NOMAD_ADDR=http://127.0.0.1:4646 NOMAD_TOKEN="$NOMAD_BOOTSTRAP_TOKEN"
curl -fsS -H "X-Nomad-Token: $NOMAD_TOKEN" "$NOMAD_ADDR/v1/jobs" | jq -r '.[].ID' | while read -r job; do
  nomad job stop -purge "$job"
done
EOF

# Stop remaining stateful host services on the VPS.
vps 'sudo systemctl stop vault nomad consul'

# Stop stateful host services on the home node.
homehost 'sudo systemctl stop nomad consul smbd adguard-home'

# Step 5: pull filesystem archives.
# Pull filesystem backups from both hosts.
infra/scripts/backup-service-state.sh

# Step 6: verify the completed run.
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
