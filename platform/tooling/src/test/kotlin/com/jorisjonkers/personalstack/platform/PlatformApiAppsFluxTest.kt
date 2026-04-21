package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformApiAppsFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `stateless apps kustomization includes first party apis`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/stateless/kustomization.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("- auth-api")
            .contains("- assistant-api")
    }

    @Test
    fun `auth api is deployed with vault injected config and in cluster dependencies`() {
        val manifest = repositoryRoot.resolve("platform/cluster/flux/apps/stateless/auth-api/deployment.yaml").toFile().readText()

        assertThat(manifest)
            .contains("kind: ServiceAccount")
            .contains("name: auth-api")
            .contains("namespace: auth-system")
            .contains("kind: Deployment")
            .contains("ghcr.io/extratoast/personal-stack/auth-api:latest")
            .contains("vault.hashicorp.com/agent-inject:")
            .contains("vault.hashicorp.com/agent-inject-token:")
            .contains("vault.hashicorp.com/role: auth-api")
            .contains("/vault/secrets/auth-api.env")
            .contains("postgres.data-system.svc.cluster.local")
            .contains("rabbitmq.data-system.svc.cluster.local")
            .contains("valkey.data-system.svc.cluster.local")
            .contains("stalwart.mail-system.svc.cluster.local")
            .contains("alloy.observability.svc.cluster.local:4318")
            .contains("containerPort: 8081")
            .contains("path: /api/actuator/health/liveness")
            .contains("kind: Service")
            .contains("name: auth-api")
            .contains("port: 8081")
    }

    @Test
    fun `assistant api is deployed with vault injected config and data system dependencies`() {
        val manifest = repositoryRoot.resolve("platform/cluster/flux/apps/stateless/assistant-api/deployment.yaml").toFile().readText()

        assertThat(manifest)
            .contains("kind: ServiceAccount")
            .contains("name: assistant-api")
            .contains("namespace: assistant-system")
            .contains("kind: Deployment")
            .contains("ghcr.io/extratoast/personal-stack/assistant-api:latest")
            .contains("vault.hashicorp.com/agent-inject:")
            .contains("vault.hashicorp.com/agent-inject-token:")
            .contains("vault.hashicorp.com/role: assistant-api")
            .contains("/vault/secrets/assistant-api.env")
            .contains("postgres.data-system.svc.cluster.local")
            .contains("rabbitmq.data-system.svc.cluster.local")
            .contains("valkey.data-system.svc.cluster.local")
            .contains("alloy.observability.svc.cluster.local:4318")
            .contains("containerPort: 8082")
            .contains("path: /api/actuator/health/liveness")
            .contains("kind: Service")
            .contains("name: assistant-api")
            .contains("port: 8082")
    }

    @Test
    fun `vault bootstrap configures api policies kubernetes roles and transit signing key`() {
        val bootstrapScript =
            repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/bootstrap-auth.sh").toFile().readText()

        assertThat(bootstrapScript)
            .contains("transit/keys/auth-api-jwt")
            .contains("vault policy write auth-api")
            .contains("vault policy write assistant-api")
            .contains("auth/kubernetes/role/auth-api")
            .contains("auth/kubernetes/role/assistant-api")
            .contains("database/creds/auth-api")
            .contains("database/creds/assistant-api")
            .contains("rabbitmq/creds/app-consumer")
            .contains("vault policy write stalwart")
            .contains("auth/kubernetes/role/stalwart")
    }
}
