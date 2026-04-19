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

    @Test
    fun `vault app bootstraps kubernetes auth and a sample readable secret`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/kustomization.yaml").toFile().readText()
        val bootstrapScript =
            repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/bootstrap-auth-configmap.yaml").toFile().readText()
        val bootstrapJob =
            repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/bootstrap-auth-job.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("bootstrap-auth-configmap.yaml")
            .contains("bootstrap-auth-job.yaml")

        assertThat(bootstrapScript)
            .contains("kind: ConfigMap")
            .contains("name: vault-bootstrap-auth")
            .contains("vault auth enable kubernetes")
            .contains("vault write auth/kubernetes/config")
            .contains("vault secrets enable -path=kvv2 kv-v2")
            .contains("vault kv put kvv2/platform/sample")
            .contains("vault policy write platform-sample-read")
            .contains("auth/kubernetes/role/platform-sample")

        assertThat(bootstrapJob)
            .contains("kind: ServiceAccount")
            .contains("name: vault-bootstrap-auth")
            .contains("kind: ClusterRoleBinding")
            .contains("name: vault-bootstrap-auth-token-reviewer")
            .contains("name: system:auth-delegator")
            .contains("kind: Job")
            .contains("name: vault-bootstrap-auth")
            .contains("valueFrom:")
            .contains("secretKeyRef:")
            .contains("name: vault-bootstrap-token")
            .contains("key: token")
    }

    @Test
    fun `vault app includes an injector based sample workload for kubernetes auth`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/kustomization.yaml").toFile().readText()
        val sampleWorkload =
            repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/sample-secret-reader.yaml").toFile().readText()

        assertThat(kustomization).contains("sample-secret-reader.yaml")

        assertThat(sampleWorkload)
            .contains("kind: ServiceAccount")
            .contains("name: platform-vault-sample")
            .contains("kind: Deployment")
            .contains("vault.hashicorp.com/agent-inject:")
            .contains("vault.hashicorp.com/role: platform-sample")
            .contains("vault.hashicorp.com/agent-inject-secret-sample.txt: kvv2/data/platform/sample")
            .contains("name: sample-reader")
    }

    @Test
    fun `cluster bootstrap readme documents vault unseal and auth bootstrap prerequisites`() {
        val readme = repositoryRoot.resolve("platform/cluster/bootstrap/README.md").toFile().readText()

        assertThat(readme)
            .contains("Vault unseal")
            .contains("vault operator init")
            .contains("vault operator unseal")
            .contains("vault-bootstrap-token")
            .contains("vault-bootstrap-auth")
    }
}
