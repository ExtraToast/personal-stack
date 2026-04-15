package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformMailFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `production cluster kustomization includes mail apps`() {
        val clusterKustomization =
            repositoryRoot.resolve("platform/cluster/flux/clusters/production/kustomization.yaml").toFile().readText()
        val appKustomization = repositoryRoot.resolve("platform/cluster/flux/apps/mail/kustomization.yaml").toFile().readText()
        val namespace = repositoryRoot.resolve("platform/cluster/flux/apps/mail/namespace.yaml").toFile().readText()

        assertThat(clusterKustomization).contains("- ../../apps/mail")
        assertThat(appKustomization)
            .contains("namespace.yaml")
            .contains("- stalwart")
        assertThat(namespace)
            .contains("kind: Namespace")
            .contains("name: mail-system")
    }

    @Test
    fun `stalwart runs in frankfurt with vault delivered config persistence and mail service ports`() {
        val kustomization =
            repositoryRoot.resolve("platform/cluster/flux/apps/mail/stalwart/kustomization.yaml").toFile().readText()
        val configTemplate =
            repositoryRoot.resolve("platform/cluster/flux/apps/mail/stalwart/config-template-configmap.yaml").toFile().readText()
        val manifest =
            repositoryRoot.resolve("platform/cluster/flux/apps/mail/stalwart/deployment.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("config-template-configmap.yaml")
            .contains("deployment.yaml")

        assertThat(configTemplate)
            .contains("kind: ConfigMap")
            .contains("name: stalwart-config-template")
            .contains("server.listener.smtp.bind = \"[::]:25\"")
            .contains("server.listener.submission.bind = \"[::]:587\"")
            .contains("server.listener.imaptls.bind = \"[::]:993\"")
            .contains("server.listener.sieve.bind = \"[::]:4190\"")
            .contains("server.listener.http.bind = \"[::]:8080\"")
            .contains("acme.\"letsencrypt\".dns.provider = \"cloudflare\"")
            .contains("metrics.prometheus.enable = true")

        assertThat(manifest)
            .contains("kind: ServiceAccount")
            .contains("name: stalwart")
            .contains("namespace: mail-system")
            .contains("kind: PersistentVolumeClaim")
            .contains("name: stalwart-data")
            .contains("kind: Deployment")
            .contains("stalwartlabs/stalwart:latest")
            .contains("vault.hashicorp.com/role: stalwart")
            .contains("/vault/secrets/stalwart.env")
            .contains("secret/data/platform/mail")
            .contains("secret/data/platform/edge")
            .contains("STALWART_HOSTNAME=mail.jorisjonkers.dev")
            .contains("personal-stack/site: frankfurt")
            .contains("claimName: stalwart-data")
            .contains("containerPort: 25")
            .contains("containerPort: 587")
            .contains("containerPort: 993")
            .contains("containerPort: 4190")
            .contains("containerPort: 8080")
            .contains("kind: Service")
            .contains("name: stalwart")
            .contains("port: 25")
            .contains("port: 587")
            .contains("port: 993")
            .contains("port: 4190")
            .contains("port: 8080")
    }

    @Test
    fun `vault bootstrap configures stalwart kubernetes auth and mail secret access`() {
        val bootstrapScript =
            repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/bootstrap-auth-configmap.yaml").toFile().readText()

        assertThat(bootstrapScript)
            .contains("vault policy write stalwart")
            .contains("auth/kubernetes/role/stalwart")
            .contains("secret/data/platform/mail")
            .contains("secret/data/platform/edge")
            .contains("bound_service_account_names=\"stalwart\"")
            .contains("bound_service_account_namespaces=\"mail-system\"")
    }
}
