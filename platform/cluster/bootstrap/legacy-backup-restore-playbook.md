# Legacy Backup Restore Playbook

This playbook maps the confirmed Nomad-era backup run at
`backups/run-20260416` onto the current `k3s` manifests.

## What Restores Cleanly Into The Current `k3s` Layout

- `Vault` through the raft snapshot
- `PostgreSQL`, `RabbitMQ`, `Valkey` through PVC restores
- `n8n`, `Uptime Kuma`, `Stalwart` through PVC restores
- Enschede media app configs through the current host-path layout

## What Does Not Restore One-To-One

- `/mnt/media` is intentionally excluded from the default backup set
- `Traefik` file state is not part of the current `k3s` design; the cluster now
  relies on `cert-manager` instead of restoring the old Nomad `traefik` data dir
- `Headscale` is no longer part of the planned platform and does not need to be
  restored into the current stack
- home-node `AdGuard`, `Samba`, and the old Nomad/Consul client state are
  outside the current `k3s` workload set

## Preconditions

1. The cluster is up and `kubectl` points at it.
2. Flux or manual applies have already created the target namespaces and PVCs.
3. Keep the target workloads scaled down until the restore finishes.
4. For media host-path restores, the Enschede utility host already exists and is
   reachable over SSH.
5. For `Vault` restore, `vault-0` exists, is initialized, and is unsealed.

## Fixed PVC Restores

Run this first for the fixed-name PVC-backed workloads:

```bash
platform/scripts/restore/restore-k3s-pvcs.sh \
  --backup-dir backups/run-20260416
```

That restores:

- `data-system/postgres-data` from `cloud/postgres.tar.gz`
- `data-system/rabbitmq-data` from `cloud/rabbitmq.tar.gz`
- `data-system/valkey-data` from `cloud/valkey.tar.gz`
- `automation-system/n8n-data` from `cloud/n8n.tar.gz`
- `observability/uptime-kuma-data` from `cloud/uptime-kuma.tar.gz`
- `mail-system/stalwart-data` from `cloud/stalwart.tar.gz`

## Vault Snapshot Restore

Restore the authoritative Vault raft snapshot after `vault-0` is running and
unsealed. Use a token that is valid inside the new cluster.

```bash
export VAULT_TOKEN='<new-cluster-vault-token>'

platform/scripts/restore/restore-vault-raft-snapshot.sh \
  --snapshot backups/run-20260416/cloud/vault-raft.snapshot
```

After the snapshot restore, re-run the cluster bootstrap auth job or the
equivalent `Vault` bootstrap step so the `kubernetes` auth backend and cluster
roles are present again for the new `k3s` service accounts.

## RabbitMQ Definitions Import

The on-disk RabbitMQ restore is usually enough, but keep the definitions export
as a recovery path.

```bash
platform/scripts/restore/restore-rabbitmq-definitions.sh \
  --definitions backups/run-20260416/cloud/rabbitmq-definitions.json \
  --username '<rabbitmq-user>' \
  --password '<rabbitmq-password>'
```

Use the credentials that the new `k3s` deployment reads from
`secret/data/platform/rabbitmq` in `Vault`.

## Enschede Media Host Paths

These apps use host paths in the current `k3s` manifests instead of PVCs, so
restore them onto the target Enschede utility node over SSH.

```bash
platform/scripts/restore/restore-media-hostpaths.sh \
  --backup-dir backups/run-20260416 \
  --ssh-target deploy@enschede-t1000-1 \
  --ssh-port 2222 \
  --identity-file ~/.ssh/ps-t1000
```

That restores:

- `/var/lib/personal-stack/media/qbittorrent`
- `/var/lib/personal-stack/media/prowlarr`
- `/var/lib/personal-stack/media/bazarr`
- `/var/lib/personal-stack/media/sonarr`
- `/var/lib/personal-stack/media/radarr`
- `/var/lib/personal-stack/media/jellyfin`
- `/var/lib/personal-stack/media/jellyseerr`

## Optional Observability PVC Restores

The Helm-managed observability charts create PVC names dynamically, so restore
those only after checking the actual claim names:

```bash
kubectl get pvc -n observability
```

Then restore each PVC with the generic helper:

```bash
platform/scripts/restore/restore-pvc-archive.sh \
  --namespace observability \
  --pvc-match grafana \
  --archive backups/run-20260416/cloud/grafana.tar.gz \
  --strip-components 3 \
  --wipe-target

platform/scripts/restore/restore-pvc-archive.sh \
  --namespace observability \
  --pvc-match loki \
  --archive backups/run-20260416/cloud/loki.tar.gz \
  --strip-components 3 \
  --wipe-target

platform/scripts/restore/restore-pvc-archive.sh \
  --namespace observability \
  --pvc-match tempo \
  --archive backups/run-20260416/cloud/tempo.tar.gz \
  --strip-components 3 \
  --wipe-target

platform/scripts/restore/restore-pvc-archive.sh \
  --namespace observability \
  --pvc-match prometheus \
  --archive backups/run-20260416/cloud/prometheus.tar.gz \
  --strip-components 3 \
  --wipe-target
```

## Recommended Order

1. Restore fixed PVC-backed services.
2. Restore `Vault` from the raft snapshot.
3. Recreate the new-cluster `Vault` auth/bootstrap config.
4. Restore media host paths on the Enschede node.
5. Start `PostgreSQL`, `RabbitMQ`, `Valkey`, `n8n`, `Uptime Kuma`, and `Stalwart`.
6. Re-import RabbitMQ definitions if the broker state is incomplete.
7. Restore optional observability PVCs only if you want the old dashboards and TSDB data immediately.
