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
- home-media application configs and `/mnt/media`
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
BACKUP_CLOUD_SSH_IDENTITY_FILE="$HOME/.ssh/ps-frankfurt" \
BACKUP_HOME_SSH_HOST=<y50-host-or-ip> \
BACKUP_HOME_SSH_USER=<user> \
BACKUP_HOME_SSH_PORT=<port> \
BACKUP_HOME_SSH_IDENTITY_FILE="$HOME/.ssh/ps-y50" \
BACKUP_OUTPUT_DIR="$RUN_DIR" \
infra/scripts/backup-service-snapshots.sh

BACKUP_CLOUD_SSH_HOST=167.86.79.203 \
BACKUP_CLOUD_SSH_USER=deploy \
BACKUP_CLOUD_SSH_PORT=2222 \
BACKUP_CLOUD_SSH_IDENTITY_FILE="$HOME/.ssh/ps-frankfurt" \
BACKUP_HOME_SSH_HOST=<y50-host-or-ip> \
BACKUP_HOME_SSH_USER=<user> \
BACKUP_HOME_SSH_PORT=<port> \
BACKUP_HOME_SSH_IDENTITY_FILE="$HOME/.ssh/ps-y50" \
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
