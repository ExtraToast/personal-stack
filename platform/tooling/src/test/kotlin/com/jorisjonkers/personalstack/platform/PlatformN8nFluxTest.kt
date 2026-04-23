package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformN8nFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `stateless apps include n8n automation workload`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/stateless/kustomization.yaml").toFile().readText()

        assertThat(kustomization).contains("- n8n")
    }

    @Test
    fun `n8n runs in automation system with persistence oidc hook and vault delivered secrets`() {
        val namespace = repositoryRoot.resolve("platform/cluster/flux/apps/stateless/n8n/namespace.yaml").toFile().readText()
        val manifest = repositoryRoot.resolve("platform/cluster/flux/apps/stateless/n8n/deployment.yaml").toFile().readText()
        val hookConfig = repositoryRoot.resolve("platform/cluster/flux/apps/stateless/n8n/hooks-configmap.yaml").toFile().readText()
        val vaultSecrets =
            repositoryRoot.resolve("platform/cluster/flux/apps/stateless/n8n/vault-secrets.yaml").toFile().readText()
        val kustomization =
            repositoryRoot.resolve("platform/cluster/flux/apps/stateless/n8n/kustomization.yaml").toFile().readText()

        assertThat(namespace)
            .contains("kind: Namespace")
            .contains("name: automation-system")

        assertThat(kustomization).contains("- vault-secrets.yaml")

        assertThat(hookConfig)
            .contains("kind: ConfigMap")
            .contains("name: n8n-hooks")
            .contains("hooks.js: |")
            .contains("External hooks that add OIDC login")
            .contains("app.get('/auth/oidc/login'")
            .contains("app.get('/assets/oidc-frontend-hook.js'")

        assertThat(vaultSecrets)
            .contains("kind: VaultDynamicSecret")
            .contains("name: n8n-db")
            .contains("mount: database")
            .contains("path: creds/n8n")
            .contains("kind: VaultStaticSecret")
            .contains("name: n8n-app")
            .contains("path: platform/automation")
            .contains("OIDC_CLIENT_SECRET")
            .contains("N8N_ENCRYPTION_KEY")
            .contains("DB_POSTGRESDB_USER")
            .contains("DB_POSTGRESDB_PASSWORD")
            .contains("rolloutRestartTargets")
            .contains("kind: Deployment")
            .contains("name: n8n")

        assertThat(manifest)
            .contains("kind: ServiceAccount")
            .contains("name: n8n")
            .contains("namespace: automation-system")
            .contains("kind: PersistentVolumeClaim")
            .contains("name: n8n-data")
            .contains("kind: Deployment")
            .contains("name: n8n")
            .contains("n8nio/n8n:2.13.4")
            .contains("envFrom:")
            .contains("name: n8n-db")
            .contains("name: n8n-app")
            .doesNotContain("vault.hashicorp.com/agent-inject")
            .doesNotContain("/vault/secrets/n8n.env")
            .contains("postgres.data-system.svc.cluster.local")
            .contains("DB_POSTGRESDB_DATABASE")
            .contains("n8n_db")
            .contains("OIDC_ISSUER_URL")
            .contains("https://auth.jorisjonkers.dev")
            .contains("OIDC_CLIENT_ID")
            .contains("N8N_EDITOR_BASE_URL")
            .contains("EXTERNAL_HOOK_FILES")
            .contains("/data/n8n/hooks.js")
            .contains("claimName: n8n-data")
            .contains("mountPath: /data/n8n/hooks.js")
            .contains("subPath: hooks.js")
            .contains("containerPort: 5678")
            .contains("path: /healthz")
            .contains("personal-stack/site: frankfurt")
            .contains("kind: Service")
            .contains("name: n8n")
            .contains("port: 5678")
    }

    @Test
    fun `vault bootstrap configures n8n kubernetes role and policy`() {
        val bootstrapScript =
            repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/bootstrap-auth.sh").toFile().readText()

        assertThat(bootstrapScript)
            .contains("vault policy write n8n")
            .contains("auth/kubernetes/role/n8n")
            .contains("database/creds/n8n")
            .contains("secret/data/platform/automation")
            .contains("bound_service_account_names=\"n8n\"")
            .contains("bound_service_account_namespaces=\"automation-system\"")
            // VSO reads dynamic postgres creds and the automation KV path
            // on behalf of n8n; the role binding now includes automation-system.
            .contains("bound_service_account_namespaces=\"vso-system,cert-manager,external-dns,observability,automation-system\"")
    }
}
