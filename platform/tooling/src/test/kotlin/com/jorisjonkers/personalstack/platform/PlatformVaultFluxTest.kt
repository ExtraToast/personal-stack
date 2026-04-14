package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformVaultFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `production cluster kustomization includes data apps`() {
        val clusterKustomization =
            repositoryRoot.resolve("platform/cluster/flux/clusters/production/kustomization.yaml").toFile().readText()

        assertThat(clusterKustomization).contains("- ../../apps/data")
    }

    @Test
    fun `data app kustomization includes vault`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/data/kustomization.yaml").toFile().readText()
        val namespace = repositoryRoot.resolve("platform/cluster/flux/apps/data/namespace.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("namespace.yaml")
            .contains("- vault")

        assertThat(namespace)
            .contains("kind: Namespace")
            .contains("name: data-system")
    }

    @Test
    fun `vault flux app installs raft backed manual unseal vault in frankfurt`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/kustomization.yaml").toFile().readText()
        val source = repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/source.yaml").toFile().readText()
        val release = repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/release.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("source.yaml")
            .contains("release.yaml")

        assertThat(source)
            .contains("kind: HelmRepository")
            .contains("name: hashicorp")
            .contains("url: https://helm.releases.hashicorp.com")

        assertThat(release)
            .contains("kind: HelmRelease")
            .contains("name: vault")
            .contains("namespace: data-system")
            .contains("chart: vault")
            .contains("ui:")
            .contains("enabled: true")
            .contains("injector:")
            .contains("csi:")
            .contains("server:")
            .contains("ha:")
            .contains("enabled: true")
            .contains("replicas: 1")
            .contains("raft:")
            .contains("enabled: true")
            .contains("storage \"raft\"")
            .contains("service_registration \"kubernetes\"")
            .contains("nodeSelector:")
            .contains("personal-stack/site: frankfurt")
            .doesNotContain("seal \"")
    }
}
