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
- `Traefik`, `Grafana`, `Loki`, `Tempo`, `Alloy`, `n8n`
- `Vault`, `Consul`, and `Nomad` data directories
- home-media application configs and `/mnt/media`
- host-local `AdGuard`, `Samba`, and legacy bootstrap env/key files

## Usage

List the scope before running anything:

```bash
infra/scripts/backup-service-state.sh --list
```

Back up everything into a timestamped folder under `backups/`:

```bash
BACKUP_CLOUD_SSH_HOST=167.86.79.203 \
BACKUP_CLOUD_SSH_USER=deploy \
BACKUP_CLOUD_SSH_PORT=2222 \
BACKUP_CLOUD_SSH_IDENTITY_FILE="$HOME/.ssh/ps-frankfurt" \
BACKUP_HOME_SSH_HOST=<y50-host-or-ip> \
BACKUP_HOME_SSH_USER=<user> \
BACKUP_HOME_SSH_PORT=<port> \
BACKUP_HOME_SSH_IDENTITY_FILE="$HOME/.ssh/ps-y50" \
infra/scripts/backup-service-state.sh
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

- The script creates filesystem backups with remote `tar` and local `gzip`.
- For write-heavy services such as `PostgreSQL`, `RabbitMQ`, `Vault`, and
  `Stalwart`, a live filesystem copy is better than nothing but is not as safe
  as a quiesced backup window. If you want the highest-confidence backup,
  stop or pause those services before running the archive pull.
- `archives.tsv` and `checksums.sha256` are written into each backup run folder.
