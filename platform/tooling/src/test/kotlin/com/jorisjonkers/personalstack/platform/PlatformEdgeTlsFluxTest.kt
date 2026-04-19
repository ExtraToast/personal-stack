package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformEdgeTlsFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `core flux kustomization includes cert-manager`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/core/kustomization.yaml").toFile().readText()

        assertThat(kustomization).contains("- cert-manager")
    }

    @Test
    fun `cert-manager flux app installs cert-manager through helm`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/core/cert-manager/kustomization.yaml").toFile().readText()
        val namespace = repositoryRoot.resolve("platform/cluster/flux/apps/core/cert-manager/namespace.yaml").toFile().readText()
        val source = repositoryRoot.resolve("platform/cluster/flux/apps/core/cert-manager/source.yaml").toFile().readText()
        val release = repositoryRoot.resolve("platform/cluster/flux/apps/core/cert-manager/release.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("namespace.yaml")
            .contains("source.yaml")
            .contains("release.yaml")

        assertThat(namespace)
            .contains("kind: Namespace")
            .contains("name: cert-manager")

        assertThat(source)
            .contains("kind: HelmRepository")
            .contains("name: jetstack")
            .contains("url: https://charts.jetstack.io")

        assertThat(release)
            .contains("kind: HelmRelease")
            .contains("name: cert-manager")
            .contains("namespace: cert-manager")
            .contains("chart: cert-manager")
            .contains("crds:")
            .contains("enabled: true")
    }

    @Test
    fun `edge app publishes wildcard tls and default traefik tls store`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/edge/kustomization.yaml").toFile().readText()
        val issuer = repositoryRoot.resolve("platform/cluster/flux/apps/edge/cluster-issuer-cloudflare.yaml").toFile().readText()
        val tls = repositoryRoot.resolve("platform/cluster/flux/apps/edge/traefik-default-tls.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("cluster-issuer-cloudflare.yaml")
            .contains("traefik-default-tls.yaml")

        assertThat(issuer)
            .contains("kind: ClusterIssuer")
            .contains("name: letsencrypt-cloudflare")
            .contains("server: https://acme-v02.api.letsencrypt.org/directory")
            .contains("email: postmaster@jorisjonkers.dev")
            .contains("apiTokenSecretRef:")
            .contains("name: cloudflare-dns-api-token")
            .contains("key: api-token")

        assertThat(tls)
            .contains("kind: Certificate")
            .contains("name: wildcard-jorisjonkers-dev")
            .contains("namespace: edge-system")
            .contains("secretName: jorisjonkers-dev-tls")
            .contains("name: letsencrypt-cloudflare")
            .contains("- jorisjonkers.dev")
            .contains("- '*.jorisjonkers.dev'")
            .contains("kind: TLSStore")
            .contains("name: default")
            .contains("defaultCertificate:")
            .contains("secretName: jorisjonkers-dev-tls")
    }
}
