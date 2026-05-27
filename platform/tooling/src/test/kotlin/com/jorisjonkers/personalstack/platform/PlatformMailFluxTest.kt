package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformMailFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `production cluster wires the mail apps flux kustomization`() {
        val kustomizations =
            repositoryRoot.resolve("platform/cluster/flux/clusters/production/kustomizations.yaml").toFile().readText()
        val appKustomization = repositoryRoot.resolve("platform/cluster/flux/apps/mail/kustomization.yaml").toFile().readText()
        val namespace = repositoryRoot.resolve("platform/cluster/flux/apps/mail/namespace.yaml").toFile().readText()

        assertThat(kustomizations)
            .contains("name: apps-mail")
            .contains("path: ./platform/cluster/flux/apps/mail")
        assertThat(appKustomization)
            .contains("namespace.yaml")
            .contains("- stalwart")
        assertThat(namespace)
            .contains("kind: Namespace")
            .contains("name: mail-system")
    }

    @Test
    fun `stalwart runs v0_16 in frankfurt with declarative config and mail service ports`() {
        val base = "platform/cluster/flux/apps/mail/stalwart"
        val kustomization = repositoryRoot.resolve("$base/kustomization.yaml").toFile().readText()
        val configJson = repositoryRoot.resolve("$base/config-json-configmap.yaml").toFile().readText()
        val planTemplate = repositoryRoot.resolve("$base/plan.ndjson.tmpl").toFile().readText()
        val applyScript = repositoryRoot.resolve("$base/apply.sh").toFile().readText()
        val manifest = repositoryRoot.resolve("$base/deployment.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("config-json-configmap.yaml")
            .contains("vault-static-secrets.yaml")
            .contains("deployment.yaml")
            .contains("apply.sh")
            .contains("plan.ndjson.tmpl")

        assertThat(configJson)
            .contains("name: stalwart-config-json")
            .contains("\"@type\": \"RocksDb\"")
            .contains("/var/lib/stalwart/data")

        assertThat(planTemplate)
            .contains("\"object\":\"DnsServer\"")
            .contains("\"@type\":\"Cloudflare\"")
            .contains("\${CF_DNS_API_TOKEN}")
            .contains("\"object\":\"AcmeProvider\"")
            .contains("\"challengeType\":\"Dns01\"")
            .contains("[::]:587")
            .contains("[::]:143")

        assertThat(applyScript)
            .contains("stalwart-cli")
            .contains("CF_DNS_API_TOKEN")
            .contains("DnsServer")
            .contains("AUTH_MAIL_PASSWORD")

        assertThat(manifest)
            .contains("kind: ServiceAccount")
            .contains("name: stalwart")
            .contains("namespace: mail-system")
            .contains("kind: PersistentVolumeClaim")
            .contains("name: stalwart-data")
            .contains("kind: Deployment")
            .contains("stalwartlabs/stalwart:v0.16.6")
            .contains("mountPath: /etc/stalwart/config.json")
            .contains("mountPath: /var/lib/stalwart")
            .contains("name: STALWART_RECOVERY_ADMIN")
            .contains("name: stalwart-apply")
            .contains("personal-stack/site: frankfurt")
            .contains("claimName: stalwart-data")
            .contains("containerPort: 25")
            .contains("containerPort: 587")
            .contains("containerPort: 993")
            .contains("containerPort: 4190")
            .contains("containerPort: 8080")
            .contains("kind: Service")
            .contains("port: 25")
            .contains("port: 587")
            .contains("port: 993")
            .contains("port: 4190")
            .contains("port: 8080")
    }

    @Test
    fun `stalwart secrets are vault-synced with rollout restart on rotation`() {
        val vss =
            repositoryRoot.resolve("platform/cluster/flux/apps/mail/stalwart/vault-static-secrets.yaml").toFile().readText()
        val vaultBootstrap =
            repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/bootstrap-auth.sh").toFile().readText()

        assertThat(vss)
            .contains("kind: VaultStaticSecret")
            .contains("name: stalwart-edge")
            .contains("name: stalwart-mail")
            .contains("path: platform/edge")
            .contains("path: platform/mail")
            .contains("rolloutRestartTargets")
            .contains("kind: Deployment")
            .contains("name: stalwart")

        assertThat(vaultBootstrap)
            .contains("mail-system")
    }

    @Test
    fun `vault bootstrap configures stalwart kubernetes auth and mail secret access`() {
        val bootstrapScript =
            repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/bootstrap-auth.sh").toFile().readText()

        assertThat(bootstrapScript)
            .contains("vault policy write stalwart")
            .contains("auth/kubernetes/role/stalwart")
            .contains("secret/data/platform/mail")
            .contains("secret/data/platform/edge")
            .contains("bound_service_account_names=\"stalwart\"")
            .contains("bound_service_account_namespaces=\"mail-system\"")
    }
}
