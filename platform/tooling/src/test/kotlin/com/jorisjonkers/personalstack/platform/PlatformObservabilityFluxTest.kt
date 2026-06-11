package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformObservabilityFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `gatus deploys as the fleet-driven status page under the status subdomain`() {
        val kustomization =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/gatus/kustomization.yaml").toFile().readText()
        val deployment =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/gatus/deployment.yaml").toFile().readText()
        val config =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/gatus/gatus-config-configmap.yaml").toFile().readText()
        val endpoints =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/gatus/gatus-endpoints-configmap.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("deployment.yaml")
            .contains("gatus-config-configmap.yaml")
            .contains("gatus-endpoints-configmap.yaml")

        assertThat(deployment)
            .contains("kind: PersistentVolumeClaim")
            .contains("name: gatus-data")
            .contains("kind: Deployment")
            .contains("name: gatus")
            .contains("namespace: observability")
            .contains("image: twinproduction/gatus:")
            .contains("GATUS_CONFIG_PATH")
            .contains("personal-stack/site: frankfurt")
            .contains("mountPath: /data")
            .contains("mountPath: /config")

        assertThat(config)
            .contains("kind: ConfigMap")
            .contains("name: gatus-config")
            .contains("storage:")
            .contains("type: sqlite")
            .contains("path: /data/data.db")

        assertThat(endpoints)
            .contains("kind: ConfigMap")
            .contains("name: gatus-endpoints")
            .contains("namespace: observability")
            .contains("endpoints.yaml: |")
            .contains("endpoints:")
    }

    @Test
    fun `backup jobs define vault postgres rabbitmq and pvc snapshot schedules`() {
        val backups =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/backup-jobs.yaml").toFile().readText()

        assertThat(backups)
            .contains("kind: PersistentVolumeClaim")
            .contains("name: observability-backups")
            .contains("kind: CronJob")
            .contains("name: vault-raft-snapshot")
            .contains("name: postgres-logical-backup")
            .contains("name: rabbitmq-definitions-backup")
            .contains("name: pvc-snapshot-backup")
            .contains("vault-backup-token")
            .contains("postgres-backup")
            .contains("rabbitmq-backup")
            .contains("vault-data-weekly")
            .contains("postgres-data-weekly")
            .contains("rabbitmq-data-weekly")
            .contains("stalwart-data-weekly")
            .contains("valkey-data-weekly")
            .contains("vault-data-weekly data-system data-vault-0")
            .contains("postgres-data-weekly data-system postgres-data")
            .contains("rabbitmq-data-weekly data-system rabbitmq-data")
            .contains("stalwart-data-weekly mail-system stalwart-data")
            .contains("valkey-data-weekly data-system valkey-data")
            .contains("bitnami/kubectl")
            .contains("suspend: true")
    }
}
