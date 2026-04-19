package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformDataServicesFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `data apps kustomization includes postgres rabbitmq and valkey`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/data/kustomization.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("- postgres")
            .contains("- rabbitmq")
            .contains("- valkey")
    }

    @Test
    fun `postgres runs as a stateful workload with init script and persistent storage`() {
        val manifest = repositoryRoot.resolve("platform/cluster/flux/apps/data/postgres/deployment.yaml").toFile().readText()
        val initScript = repositoryRoot.resolve("platform/cluster/flux/apps/data/postgres/init-script-configmap.yaml").toFile().readText()

        assertThat(initScript)
            .contains("kind: ConfigMap")
            .contains("name: postgres-init-script")
            .contains("init-databases.sh: |")
            .contains("CREATE DATABASE n8n_db OWNER")

        assertThat(manifest)
            .contains("kind: ServiceAccount")
            .contains("name: postgres")
            .contains("namespace: data-system")
            .contains("kind: PersistentVolumeClaim")
            .contains("name: postgres-data")
            .contains("kind: Deployment")
            .contains("name: postgres")
            .contains("postgres:17-alpine")
            .contains("vault.hashicorp.com/agent-inject:")
            .contains("vault.hashicorp.com/role: postgres")
            .contains("/vault/secrets/postgres.env")
            .contains("listen_addresses=*")
            .contains("mountPath: /docker-entrypoint-initdb.d/init-databases.sh")
            .contains("subPath: init-databases.sh")
            .contains("claimName: postgres-data")
            .contains("containerPort: 5432")
            .contains("kind: Service")
            .contains("name: postgres")
            .contains("port: 5432")
            .contains("personal-stack/site: frankfurt")
    }

    @Test
    fun `rabbitmq keeps management metrics and oidc config in cluster`() {
        val config = repositoryRoot.resolve("platform/cluster/flux/apps/data/rabbitmq/config-configmap.yaml").toFile().readText()
        val plugins = repositoryRoot.resolve("platform/cluster/flux/apps/data/rabbitmq/enabled-plugins-configmap.yaml").toFile().readText()
        val manifest = repositoryRoot.resolve("platform/cluster/flux/apps/data/rabbitmq/deployment.yaml").toFile().readText()

        assertThat(config)
            .contains("kind: ConfigMap")
            .contains("name: rabbitmq-config")
            .contains("rabbitmq.conf: |")
            .contains("auth_oauth2.resource_server_id = rabbitmq")
            .contains("management.oauth_client_id = rabbitmq")

        assertThat(plugins)
            .contains("kind: ConfigMap")
            .contains("name: rabbitmq-enabled-plugins")
            .contains("enabled_plugins: |")
            .contains("rabbitmq_prometheus")

        assertThat(manifest)
            .contains("kind: ServiceAccount")
            .contains("name: rabbitmq")
            .contains("namespace: data-system")
            .contains("kind: PersistentVolumeClaim")
            .contains("name: rabbitmq-data")
            .contains("kind: Deployment")
            .contains("rabbitmq:4.2-management-alpine")
            .contains("vault.hashicorp.com/agent-inject:")
            .contains("vault.hashicorp.com/role: rabbitmq")
            .contains("/vault/secrets/rabbitmq.env")
            .contains("mountPath: /etc/rabbitmq/rabbitmq.conf")
            .contains("mountPath: /etc/rabbitmq/enabled_plugins")
            .contains("containerPort: 5672")
            .contains("containerPort: 15672")
            .contains("containerPort: 15692")
            .contains("kind: Service")
            .contains("name: rabbitmq")
            .contains("port: 15672")
            .contains("name: management")
            .contains("name: metrics")
            .contains("personal-stack/site: frankfurt")
    }

    @Test
    fun `valkey is persisted as an internal stateful service`() {
        val manifest = repositoryRoot.resolve("platform/cluster/flux/apps/data/valkey/deployment.yaml").toFile().readText()

        assertThat(manifest)
            .contains("kind: PersistentVolumeClaim")
            .contains("name: valkey-data")
            .contains("kind: Deployment")
            .contains("name: valkey")
            .contains("valkey/valkey:8-alpine")
            .contains("containerPort: 6379")
            .contains("claimName: valkey-data")
            .contains("kind: Service")
            .contains("name: valkey")
            .contains("port: 6379")
            .contains("personal-stack/site: frankfurt")
    }

    @Test
    fun `vault bootstrap configures postgres and rabbitmq policies and kubernetes roles`() {
        val bootstrapScript =
            repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/bootstrap-auth-configmap.yaml").toFile().readText()

        assertThat(bootstrapScript)
            .contains("vault policy write postgres")
            .contains("vault policy write rabbitmq")
            .contains("auth/kubernetes/role/postgres")
            .contains("auth/kubernetes/role/rabbitmq")
            .contains("secret/data/platform/postgres")
            .contains("secret/data/platform/rabbitmq")
            .contains("bound_service_account_names=\"postgres\"")
            .contains("bound_service_account_names=\"rabbitmq\"")
    }

    @Test
    fun `data service playbook documents backup restore and rollback for each datastore`() {
        val readme = repositoryRoot.resolve("platform/cluster/bootstrap/README.md").toFile().readText()
        val playbook =
            repositoryRoot.resolve("platform/cluster/bootstrap/data-services-playbook.md").toFile().readText()

        assertThat(readme)
            .contains("Data Service Cutover And Recovery")
            .contains("data-services-playbook.md")

        assertThat(playbook)
            .contains("PostgreSQL")
            .contains("RabbitMQ")
            .contains("Valkey")
            .contains("postgres-logical-backup")
            .contains("rabbitmq-definitions-backup")
            .contains("pvc-snapshot-backup")
            .contains("postgres-data")
            .contains("rabbitmq-data")
            .contains("valkey-data")
            .contains("restore")
            .contains("rollback")
            .contains("Rehearsal Record")
    }
}
