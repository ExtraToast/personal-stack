# Service Backups

This directory is the local landing zone for migration backups pulled from the
old Nomad-era hosts.

- Generated backup archives are written into timestamped `run-*` folders here.
- The actual backup payloads are ignored by Git on purpose.
- The committed [manifest.tsv](manifest.tsv) is the current backup scope.

## Current Scope

The manifest is split into two source groups:

- `cloud`: the Contabo VPS and its stateful Nomad/system-service data
- `home`: the old Y50-70 home node and its Nomad/media/system-service data

The current list includes:

- `Stalwart` state
- `Uptime Kuma` state
- `PostgreSQL`, `Valkey`, `RabbitMQ`
- `Prometheus`
- `Traefik`, `Grafana`, `Loki`, `Tempo`, `Alloy`, `n8n`
- `Vault`, `Consul`, and `Nomad` data directories
- home-media application configs only; bulk media storage is excluded from default backups
- host-local `AdGuard`, `Samba`, and legacy bootstrap env/key files

## Critical Order

For the control-plane services, the safest backup order is:

1. While the services are still running, capture service-native snapshots and exports.
2. Stop Nomad jobs and then stop stateful host services.
3. Pull the filesystem archives from the now-quiesced hosts.
4. Verify the captured artifacts and archive checksums before deleting or migrating anything.

This order matters because:

- `Vault`, `Consul`, and `Nomad` all have official snapshot commands that should be used before shutdown.
- `RabbitMQ` definitions export is easiest while the broker is still up.
- `PostgreSQL`, `Grafana` SQLite, `Uptime Kuma` SQLite, and `Stalwart` RocksDB are safest to copy after they stop writing.

In this repo, `n8n` state is split across two places:

- `/srv/nomad/n8n` for the `.n8n` user folder and encryption key
- `/srv/nomad/postgres` for workflows, executions, and credentials because this deployment uses PostgreSQL

## Usage

List the scope before running anything:

```bash
infra/scripts/backup-service-state.sh --list
infra/scripts/backup-service-snapshots.sh --list
```

Audit the manifest against the declared Nomad host volumes first:

```bash
infra/scripts/audit-backup-scope.sh
```

Back up everything into a single timestamped folder under `backups/`:

```bash
RUN_DIR="$PWD/backups/run-$(date -u +%Y%m%dT%H%M%SZ)"

BACKUP_CLOUD_SSH_HOST=167.86.79.203 \
BACKUP_CLOUD_SSH_USER=deploy \
BACKUP_CLOUD_SSH_PORT=2222 \
BACKUP_CLOUD_SSH_IDENTITY_FILE="$HOME/.ssh/ps-vps-1" \
BACKUP_HOME_SSH_HOST=<y50-host-or-ip> \
BACKUP_HOME_SSH_USER=<user> \
BACKUP_HOME_SSH_PORT=<port> \
BACKUP_HOME_SSH_IDENTITY_FILE="$HOME/.ssh/ps-gtx960m" \
BACKUP_OUTPUT_DIR="$RUN_DIR" \
infra/scripts/backup-service-snapshots.sh

BACKUP_CLOUD_SSH_HOST=167.86.79.203 \
BACKUP_CLOUD_SSH_USER=deploy \
BACKUP_CLOUD_SSH_PORT=2222 \
BACKUP_CLOUD_SSH_IDENTITY_FILE="$HOME/.ssh/ps-vps-1" \
BACKUP_HOME_SSH_HOST=<y50-host-or-ip> \
BACKUP_HOME_SSH_USER=<user> \
BACKUP_HOME_SSH_PORT=<port> \
BACKUP_HOME_SSH_IDENTITY_FILE="$HOME/.ssh/ps-gtx960m" \
BACKUP_OUTPUT_DIR="$RUN_DIR" \
infra/scripts/backup-service-state.sh

infra/scripts/verify-backup-run.sh "$RUN_DIR"
```

Back up just one side:

```bash
infra/scripts/backup-service-state.sh --host cloud
infra/scripts/backup-service-state.sh --host home
```

Back up only specific services:

```bash
infra/scripts/backup-service-state.sh --service stalwart --service uptime-kuma
```

## Tested Remote Commands

These are the copy-paste-safe remote commands for the current backup flow.

Check passwordless sudo on both hosts:

```bash
ssh -i ~/.ssh/ps-vps-1 -p 2222 deploy@100.64.0.1 'sudo -n true'
ssh -i ~/.ssh/ps-gtx960m -p 22 extratoast@100.64.0.2 'sudo -n true'
```

Check that the Vault token file on the VPS still works:

```bash
ssh -i ~/.ssh/ps-vps-1 -p 2222 deploy@100.64.0.1 \
  "sudo bash -lc 'source /opt/personal-stack/.vault-keys; export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=\$VAULT_ROOT_TOKEN; vault status >/dev/null && echo VAULT_OK'"
```

Check that RabbitMQ credentials can be resolved from Vault and used locally:

```bash
ssh -i ~/.ssh/ps-vps-1 -p 2222 deploy@100.64.0.1 \
  "sudo bash -lc 'source /opt/personal-stack/.vault-keys; export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=\$VAULT_ROOT_TOKEN; rmq_user=\$(vault kv get -field=rabbitmq.user secret/platform/rabbitmq); rmq_password=\$(vault kv get -field=rabbitmq.password secret/platform/rabbitmq); curl -fsS --user \"\$rmq_user:\$rmq_password\" http://127.0.0.1:15672/api/overview >/dev/null && echo RABBITMQ_OK'"
```

For remote Nomad checks on the VPS, keep `NOMAD_ADDR` and `NOMAD_TOKEN` on the
same `export` command inside the remote shell:

```bash
ssh -i ~/.ssh/ps-vps-1 -p 2222 deploy@100.64.0.1 \
  "sudo bash -lc 'source /opt/personal-stack/.nomad-keys; export NOMAD_ADDR=http://127.0.0.1:4646 NOMAD_TOKEN=\$NOMAD_BOOTSTRAP_TOKEN; nomad status >/dev/null && echo NOMAD_OK'"
```

Stopping all Nomad jobs safely uses the same pattern:

```bash
ssh -i ~/.ssh/ps-vps-1 -p 2222 deploy@100.64.0.1 \
  "sudo bash -lc 'source /opt/personal-stack/.nomad-keys; export NOMAD_ADDR=http://127.0.0.1:4646 NOMAD_TOKEN=\$NOMAD_BOOTSTRAP_TOKEN; curl -fsS -H \"X-Nomad-Token: \$NOMAD_TOKEN\" \"\$NOMAD_ADDR/v1/jobs\" | jq -r \".[].ID\" | while read -r job; do nomad job stop -purge \"\$job\"; done'"
```

Stop the remaining VPS host services:

```bash
ssh -i ~/.ssh/ps-vps-1 -p 2222 deploy@100.64.0.1 \
  'sudo systemctl stop vault nomad consul'
```

Stop the home-node services after the live snapshots complete:

```bash
ssh -i ~/.ssh/ps-gtx960m -p 22 extratoast@100.64.0.2 \
  'sudo systemctl stop nomad consul smbd adguard-home'
```

## Required Environment

For each host group you want to back up, provide either:

- `BACKUP_<GROUP>_SSH_TARGET`

or:

- `BACKUP_<GROUP>_SSH_HOST`
- `BACKUP_<GROUP>_SSH_USER`
- optional `BACKUP_<GROUP>_SSH_PORT` (defaults to `22`)
- optional `BACKUP_<GROUP>_SSH_IDENTITY_FILE`
- optional `BACKUP_<GROUP>_SSH_OPTS`
- optional `BACKUP_<GROUP>_SUDO` (defaults to `sudo -n`)

Where `<GROUP>` is `CLOUD` or `HOME`.

If you connect as `root`, disable the default sudo wrapper explicitly:

```bash
BACKUP_CLOUD_SUDO= \
BACKUP_HOME_SUDO= \
infra/scripts/backup-service-state.sh
```

## Notes

- `backup-service-snapshots.sh` writes:
  - `service-snapshots.tsv`
  - `service-snapshots.sha256`
- `backup-service-state.sh` writes:
  - `archives.tsv`
  - `checksums.sha256`
- The filesystem script creates remote `tar` streams and compresses them locally with `gzip`.
- For write-heavy services such as `PostgreSQL`, `RabbitMQ`, `Vault`, and
  `Stalwart`, a live filesystem copy is better than nothing but is not as safe
  as a quiesced backup window. If you want the highest-confidence backup,
  stop or pause those services before running the archive pull.
- `Vault` snapshots do not replace your unseal/recovery material. Keep
  `.vault-keys` or another secure copy of the unseal/root-token information.
- `RabbitMQ` definitions export complements the data directory backup; it does
  not replace the broker data volume if you need queued messages and on-disk state.
- The live snapshot script now needs only:
  - a current Vault token in `/opt/personal-stack/.vault-keys`
  - a current Nomad management token in `/opt/personal-stack/.nomad-keys`
    RabbitMQ credentials are resolved from `secret/platform/rabbitmq` in Vault.
