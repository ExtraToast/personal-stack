package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/**
 * Meta-regression test for the class of bug where a VaultStaticSecret
 * or VaultDynamicSecret is declared in namespace N but:
 *
 *  1. The `vso` Vault role's `bound_service_account_namespaces` does
 *     not include N — every login then 403s with
 *     `namespace not authorized` and the destination Secret never
 *     refreshes, OR
 *  2. There is no `ServiceAccount/vault-secrets-operator` manifest
 *     in namespace N — VSO logs
 *     `ServiceAccount "vault-secrets-operator" not found` and never
 *     even attempts a Vault login.
 *
 * Either failure mode leaves dependent workloads with stale or
 * uncreated Secrets (immich-postgres → CreateContainerConfigError,
 * vault-prometheus-token → ServiceMonitor 401s, etc.) without any
 * pod restart to draw attention. This test fails CI with the
 * specific namespace name so the gap is fixed before merge.
 */
class VsoCoverageTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    private val bootstrap: String by lazy {
        repositoryRoot
            .resolve("platform/cluster/flux/apps/data/vault/bootstrap-auth.sh")
            .toFile()
            .readText()
    }

    @Test
    fun `every namespace owning a VSO secret resource is allow-listed in the vso role`() {
        val namespaces = collectVsoSecretNamespaces()
        assertThat(namespaces)
            .describedAs(
                "expected to find at least one VaultStaticSecret/VaultDynamicSecret manifest under platform/cluster/flux",
            ).isNotEmpty

        val bound = parseVsoRoleBoundNamespaces()
        val missing = namespaces.filterNot { (ns, _) -> ns in bound }
        assertThat(missing)
            .describedAs(
                "Vault `vso` role bound_service_account_namespaces=\"${bound.joinToString(",")}\" " +
                    "is missing one or more namespaces that declare a VaultStaticSecret/VaultDynamicSecret. " +
                    "VSO will get 403 \"namespace not authorized\" from Vault and never refresh the " +
                    "destination Secret. Add the namespace(s) to the vso role in " +
                    "platform/cluster/flux/apps/data/vault/bootstrap-auth.sh.\n" +
                    "Missing: $missing",
            ).isEmpty()
    }

    @Test
    fun `every namespace owning a VSO secret resource has a vault-secrets-operator ServiceAccount manifest`() {
        val namespaces = collectVsoSecretNamespaces()
        val saNamespaces = collectVsoServiceAccountNamespaces()
        val missing = namespaces.filterNot { (ns, _) -> ns in saNamespaces }
        assertThat(missing)
            .describedAs(
                "One or more namespaces declare a VaultStaticSecret/VaultDynamicSecret but no " +
                    "`ServiceAccount/vault-secrets-operator` manifest in the same namespace. VSO " +
                    "mints its kube-auth JWT against that SA in the secret's namespace, so without " +
                    "it the controller logs `ServiceAccount \"vault-secrets-operator\" not found` " +
                    "and the Secret is never created. Add the SA next to the VSO secret manifest.\n" +
                    "Missing: $missing",
            ).isEmpty()
    }

    private data class VsoSecretRef(
        val namespace: String,
        val sourcePath: String,
    )

    private fun collectVsoSecretNamespaces(): List<VsoSecretRef> {
        val fluxApps = repositoryRoot.resolve("platform/cluster/flux/apps")
        if (!Files.exists(fluxApps)) return emptyList()

        return Files
            .walk(fluxApps)
            .filter { p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml") }
            .toList()
            .flatMap { path ->
                val text = path.toFile().readText()
                yamlDocs(text)
                    .filter { doc ->
                        val kind = matchScalar(doc, "kind")
                        kind == "VaultStaticSecret" || kind == "VaultDynamicSecret"
                    }.mapNotNull { doc ->
                        val ns = matchScalar(doc, "namespace")
                        ns?.let { VsoSecretRef(it, repositoryRoot.relativize(path).toString()) }
                    }
            }
    }

    private fun collectVsoServiceAccountNamespaces(): Set<String> {
        val fluxApps = repositoryRoot.resolve("platform/cluster/flux/apps")
        if (!Files.exists(fluxApps)) return emptySet()

        return Files
            .walk(fluxApps)
            .filter { p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml") }
            .toList()
            .flatMap { path ->
                val text = path.toFile().readText()
                yamlDocs(text)
                    .filter { doc ->
                        matchScalar(doc, "kind") == "ServiceAccount" &&
                            matchScalar(doc, "name") == "vault-secrets-operator"
                    }.mapNotNull { doc -> matchScalar(doc, "namespace") }
            }.toSet()
    }

    private fun parseVsoRoleBoundNamespaces(): List<String> {
        val roleBlock =
            Regex(
                """vault\s+write\s+auth/kubernetes/role/vso\s*\\?\n((?:\s*[^\n]+\\?\n)+)""",
            ).find(bootstrap)?.groupValues?.get(1)
                ?: error(
                    "could not find `vault write auth/kubernetes/role/vso` block in bootstrap-auth.sh — " +
                        "the role definition appears to have been renamed or removed.",
                )

        val match =
            Regex("""bound_service_account_namespaces="([^"]+)"""").find(roleBlock)
                ?: error("vso role block has no bound_service_account_namespaces=\"...\" line")
        return match.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun yamlDocs(text: String): List<String> = text.split(Regex("(?m)^---\\s*$"))

    private fun matchScalar(
        doc: String,
        key: String,
    ): String? =
        // Look only at indent 0 (kind:) or indent 2 (metadata.namespace, metadata.name).
        // Anything deeper would be a nested key on a different object.
        Regex("(?m)^( {0,2})$key:\\s*([^\\s#]+)").find(doc)?.groupValues?.get(2)
}
