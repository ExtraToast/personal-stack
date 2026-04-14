package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformExternalDnsFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `core flux kustomization includes external-dns`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/core/kustomization.yaml").toFile().readText()

        assertThat(kustomization).contains("- external-dns")
    }

    @Test
    fun `external-dns flux app installs cloudflare traefik aware dns sync`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/core/external-dns/kustomization.yaml").toFile().readText()
        val namespace = repositoryRoot.resolve("platform/cluster/flux/apps/core/external-dns/namespace.yaml").toFile().readText()
        val source = repositoryRoot.resolve("platform/cluster/flux/apps/core/external-dns/source.yaml").toFile().readText()
        val release = repositoryRoot.resolve("platform/cluster/flux/apps/core/external-dns/release.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("namespace.yaml")
            .contains("source.yaml")
            .contains("release.yaml")

        assertThat(namespace)
            .contains("kind: Namespace")
            .contains("name: external-dns")

        assertThat(source)
            .contains("kind: HelmRepository")
            .contains("name: external-dns")
            .contains("url: https://kubernetes-sigs.github.io/external-dns/")

        assertThat(release)
            .contains("kind: HelmRelease")
            .contains("name: external-dns")
            .contains("namespace: external-dns")
            .contains("chart: external-dns")
            .contains("name: cloudflare")
            .contains("- service")
            .contains("- traefik-proxy")
            .contains("name: CF_API_TOKEN")
            .contains("name: cloudflare-api-key")
            .contains("key: apiKey")
            .contains("--domain-filter=jorisjonkers.dev")
            .contains("--txt-owner-id=personal-stack-production")
            .contains("--annotation-filter=kubernetes.io/ingress.class=traefik-public")
    }

    @Test
    fun `ingress controller publishes a stable external dns service hostname`() {
        val release = repositoryRoot.resolve("platform/cluster/flux/apps/core/ingress-controller/release.yaml").toFile().readText()

        assertThat(release)
            .contains("annotations:")
            .contains("external-dns.alpha.kubernetes.io/hostname: ingress.jorisjonkers.dev")
            .contains("external-dns.alpha.kubernetes.io/cloudflare-proxied: \"true\"")
    }
}
