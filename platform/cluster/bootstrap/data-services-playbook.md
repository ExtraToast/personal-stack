# Data Service Cutover And Recovery

This playbook covers the Phase 9 stateful services in `data-system`:

- `PostgreSQL` on PVC `postgres-data`
- `RabbitMQ` on PVC `rabbitmq-data`
- `Valkey` on PVC `valkey-data`

The cluster already defines these backup entrypoints:

- `postgres-logical-backup` writes `postgres.sql` into the `observability-backups` PVC
- `rabbitmq-definitions-backup` writes `rabbitmq-definitions.json` into the `observability-backups` PVC
- `pvc-snapshot-backup` creates timestamped `VolumeSnapshot` objects for `data-vault-0`, `postgres-data`, `rabbitmq-data`, and `valkey-data`

## Shared Preconditions

1. Keep write traffic stopped before restore or rollback:
   `auth-api`, `assistant-api`, and `n8n` should be scaled to `0` before touching `PostgreSQL`.
2. Run each backup path once before the cutover window:
   `kubectl -n observability create job --from=cronjob/postgres-logical-backup postgres-logical-backup-manual-$(date +%Y%m%d%H%M%S)`
   `kubectl -n observability create job --from=cronjob/rabbitmq-definitions-backup rabbitmq-definitions-backup-manual-$(date +%Y%m%d%H%M%S)`
   `kubectl -n observability create job --from=cronjob/pvc-snapshot-backup pvc-snapshot-backup-manual-$(date +%Y%m%d%H%M%S)`
3. Record the exact backup artifacts before cutover:
   `postgres.sql`, `rabbitmq-definitions.json`, and the `VolumeSnapshot` names returned by `kubectl -n data-system get volumesnapshots`.
4. Leave the pre-cutover backup artifacts in place until validation is complete and rollback is no longer required.

## PostgreSQL

### Backup

- Use `postgres-logical-backup` for schema-and-data export.
- Use the `postgres-data-*` `VolumeSnapshot` for fast PVC rollback.

### Restore

1. Scale `auth-api`, `assistant-api`, and `n8n` to `0`.
2. Restore `postgres.sql` from the `observability-backups` PVC into a clean `postgres` instance with `psql`.
3. If the on-disk state is damaged, recreate `postgres-data` from the chosen `VolumeSnapshot` and start `postgres` again.
4. Validate application logins and database connectivity before re-enabling writers.

### Rollback

1. Keep the pre-cutover `postgres-data-*` snapshot name attached to the deployment change record.
2. Scale writers back to `0`.
3. Recreate `postgres-data` from the pre-cutover snapshot.
4. Re-run smoke checks for `auth-api`, `assistant-api`, and `n8n`, then scale them back up.

## RabbitMQ

### Backup

- Use `rabbitmq-definitions-backup` for exchanges, queues, bindings, users, and policies.
- Use the `rabbitmq-data-*` `VolumeSnapshot` when queue contents must survive rollback.

### Restore

1. Stop producers and consumers that write to `rabbitmq`.
2. Restore the `rabbitmq-data` PVC from the chosen `VolumeSnapshot` if broker state is corrupt.
3. Re-import `rabbitmq-definitions.json` through the management API after the broker is reachable again.
4. Confirm management, metrics, and AMQP health before resuming traffic.

### Rollback

1. Keep the pre-cutover `rabbitmq-data-*` snapshot and `rabbitmq-definitions.json`.
2. Scale dependent workloads down.
3. Recreate `rabbitmq-data` from the pre-cutover snapshot.
4. Re-import `rabbitmq-definitions.json` and bring consumers back one group at a time.

## Valkey

### Backup

- `Valkey` currently relies on `pvc-snapshot-backup`.
- The authoritative rollback artifact is the `valkey-data-*` `VolumeSnapshot`.

### Restore

1. Stop every workload that writes to `valkey`.
2. Recreate `valkey-data` from the selected `VolumeSnapshot`.
3. Start `valkey` and confirm the cache/session workload reconnects cleanly.

### Rollback

1. Preserve the pre-cutover `valkey-data-*` snapshot name until validation is complete.
2. Scale writers back down.
3. Recreate `valkey-data` from the pre-cutover snapshot.
4. Resume traffic only after cache/session behavior matches the pre-cutover baseline.

## Rehearsal Record

Before production cutover, record one rehearsal row per datastore with:

- date
- operator
- backup artifact names
- restore result
- rollback result
