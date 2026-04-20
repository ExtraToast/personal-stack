package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/**
 * Meta-regression test for the class of bug where a service's
 * `application.yml` declares it consumes Vault dynamic postgres
 * credentials via a specific role, the Vault policy grants read
 * on `database/creds/<role>`, but the Vault bootstrap script
 * never actually creates `database/roles/<role>`. In that
 * configuration every runtime call returns
 * `400 no role found with name "<role>"`, Spring's
 * DataSourceHealthIndicator flips DOWN, and user-facing endpoints
 * start returning 500s while kubelet probes stay green.
 *
 * For every `spring.cloud.vault.database.role: <name>` value we
 * find across `services/<svc>/src/main/resources/application.yml`,
 * assert:
 *   1. The bootstrap script writes `database/roles/<name>`.
 *   2. The role name appears in the `allowed_roles` list of the
 *      `database/config/postgres` write.
 *
 * Failing either makes the PR CI red with an explicit name, so
 * nobody has to re-discover this bug through production 500s.
 */
class VaultBootstrapCoverageTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    private val bootstrap: String by lazy {
        repositoryRoot
            .resolve("platform/cluster/flux/apps/data/vault/bootstrap-auth-configmap.yaml")
            .toFile()
            .readText()
    }

    @Test
    fun `every spring vault database role referenced by a service has a matching bootstrap role`() {
        val roles = collectDeclaredVaultDatabaseRoles()
        assertThat(roles)
            .describedAs("expected to find at least one spring.cloud.vault.database.role declaration in a service application.yml")
            .isNotEmpty

        val missing =
            roles.filterNot { (_, role) ->
                bootstrap.contains("vault write database/roles/$role")
            }
        assertThat(missing)
            .describedAs(
                "Vault bootstrap is missing `vault write database/roles/<name>` for one or more services. " +
                    "Without the role, every Spring Cloud Vault runtime call returns 400 and the DB " +
                    "health contributor flips DOWN. Add the role to " +
                    "platform/cluster/flux/apps/data/vault/bootstrap-auth-configmap.yaml.\n" +
                    "Missing: $missing",
            ).isEmpty()
    }

    @Test
    fun `every referenced role is listed in allowed_roles on database config postgres`() {
        val roles = collectDeclaredVaultDatabaseRoles()
        val allowedRolesLine =
            bootstrap
                .lineSequence()
                .firstOrNull { it.contains("allowed_roles=") }
                ?: error(
                    "no allowed_roles= line found in bootstrap-auth-configmap.yaml; " +
                        "the `vault write database/config/postgres` call appears missing or reshaped.",
                )

        val allowed =
            Regex("""allowed_roles="([^"]+)"""")
                .find(allowedRolesLine)
                ?.groupValues
                ?.get(1)
                ?.split(",")
                ?.map { it.trim() }
                ?: error("could not parse allowed_roles value out of: $allowedRolesLine")

        val notAllowed = roles.map { (_, role) -> role }.filterNot { it in allowed }
        assertThat(notAllowed)
            .describedAs(
                "Vault database/config/postgres allowed_roles=\"$allowed\" is missing one or more " +
                    "roles that services declare they consume via spring.cloud.vault.database.role. " +
                    "Update the allowed_roles list in bootstrap-auth-configmap.yaml.\n" +
                    "Not allowed: $notAllowed",
            ).isEmpty()
    }

    private fun collectDeclaredVaultDatabaseRoles(): List<Pair<String, String>> {
        val servicesDir = repositoryRoot.resolve("services")
        if (!Files.exists(servicesDir)) return emptyList()

        return Files
            .walk(servicesDir)
            .filter { p ->
                p.fileName.toString() == "application.yml" &&
                    p.toString().contains("/src/main/resources/")
            }.toList()
            .mapNotNull { path -> extractDatabaseRole(path)?.let { svcNameFor(path) to it } }
    }

    private fun svcNameFor(path: Path): String {
        val rel = repositoryRoot.relativize(path).toString()
        return rel.substringAfter("services/").substringBefore("/")
    }

    private fun extractDatabaseRole(path: Path): String? {
        // Only look at the spring.cloud.vault.database block — a top-level
        // `role:` key (e.g. under security) would otherwise false-match.
        val text = path.toFile().readText()
        val databaseBlock =
            Regex(
                """spring:\s*\n(?:.*\n)*?\s+cloud:\s*\n(?:.*\n)*?\s+vault:\s*\n(?:.*\n)*?\s+database:\s*\n((?:\s+.+\n)+)""",
                RegexOption.MULTILINE,
            ).find(text)?.groupValues?.get(1)
                ?: return null

        val roleLine = databaseBlock.lineSequence().firstOrNull { it.trim().startsWith("role:") } ?: return null
        // Strip `role:` prefix and any default-value ${VAR:foo} wrapping,
        // leaving just the literal role name. Example inputs:
        //   `        role: auth-api`
        //   `        role: ${VAULT_DB_ROLE:auth-api}`
        val raw = roleLine.substringAfter("role:").trim()
        return Regex("""\$\{[^:}]+:([^}]+)\}""").find(raw)?.groupValues?.get(1)?.trim()
            ?: raw.trim('"', '\'')
    }
}
