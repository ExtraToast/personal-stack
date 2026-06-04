@file:Suppress("DEPRECATION")

package com.jorisjonkers.personalstack.platform

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.name
import kotlin.streams.asSequence

class AgentKitManifestTest {
    private val repositoryRoot = RepositoryRootLocator().locate()
    private val manifestPath = repositoryRoot.resolve("platform/agents/kit/manifest.yaml")
    private val manifest = ObjectMapper(YAMLFactory()).readTree(manifestPath.toFile())

    @Test
    fun `manifest pins every checked-in skill hook setting and installer file`() {
        val actualRepoSkillPaths = repoSkillPaths()
        val manifestSkillPaths = manifestTargetPaths("skills").filter { it.contains("/skills/") }.toSet()

        assertThat(manifestSkillPaths)
            .describedAs("every checked-in Claude/Codex skill must be listed in the agent-kit manifest")
            .containsExactlyInAnyOrderElementsOf(actualRepoSkillPaths)

        val actualRepoHookPaths = repoHookPaths()
        val manifestHookPaths = manifestTargetPaths("hooks").filter { it.contains("/hooks/") }.toSet()

        assertThat(manifestHookPaths)
            .describedAs("every checked-in Claude/Codex hook must be listed in the agent-kit manifest")
            .containsExactlyInAnyOrderElementsOf(actualRepoHookPaths)

        val pinnedPaths = collectPinnedPaths(manifest)
        assertThat(pinnedPaths.map { it.path })
            .describedAs("manifest should pin repo settings and installer entrypoint as well as hooks and skills")
            .contains(
                ".claude/settings.json",
                ".codex/hooks.json",
                "services/knowledge-api/src/main/resources/installer/install.sh",
            )

        pinnedPaths.forEach { pinned ->
            val file = repositoryRoot.resolve(pinned.path)
            assertThat(Files.exists(file))
                .describedAs("manifest path exists: ${pinned.path}")
                .isTrue()
            assertThat(sha256(file))
                .describedAs("sha256 for ${pinned.path}")
                .isEqualTo(pinned.sha256)
        }
    }

    @Test
    fun `shared skills exist for both Claude and Codex unless a gap is explicit`() {
        val codexSkillNames = skillNamesUnder(".agents/skills")
        val claudeSkillNames = skillNamesUnder(".claude/skills")

        assertThat(codexSkillNames)
            .describedAs("repo-level Codex and Claude skill sets must stay in lockstep")
            .containsExactlyInAnyOrderElementsOf(claudeSkillNames)

        manifestItems("skills").forEach { skill ->
            assertAgentGapIsExplicit("skill ${skill["name"].asText()}", skill)
        }
        assertAgentGapIsExplicit("installer", manifest["installer"])
    }

    @Test
    fun `installer managed surfaces are listed in the manifest`() {
        val installer =
            repositoryRoot
                .resolve("services/knowledge-api/src/main/resources/installer/install.sh")
                .toFile()
                .readText()

        val installedSkillNames =
            Regex("""\$\{(?:CODEX_)?SKILLS_DIR}/([^/]+)/SKILL\.md""")
                .findAll(installer)
                .map { it.groupValues[1] }
                .toSet()
        val manifestInstallerSkillNames =
            manifestItems("skills")
                .filter { it.has("installer") }
                .map { it["name"].asText() }
                .toSet()

        assertThat(manifestInstallerSkillNames)
            .describedAs("every installer-managed skill must be visible in the agent-kit manifest")
            .containsExactlyInAnyOrderElementsOf(installedSkillNames)

        val installedHookNames =
            Regex("""\$\{(?:CODEX_)?HOOKS_DIR}/([^"]+\.sh)""")
                .findAll(installer)
                .map { it.groupValues[1] }
                .toSet()
        val manifestInstallerHookNames =
            manifestItems("hooks")
                .flatMap { hook ->
                    val installer = hook["installer"] ?: return@flatMap emptySequence<String>()
                    sequenceOf("target_path", "codex_target_path")
                        .mapNotNull { field -> installer[field]?.asText() }
                }
                .map { it.substringAfterLast("/") }
                .toSet()

        assertThat(manifestInstallerHookNames)
            .describedAs("every installer-managed hook must be visible in the agent-kit manifest")
            .containsExactlyInAnyOrderElementsOf(installedHookNames)
    }

    @Test
    fun `hook settings reference only manifest hooks`() {
        val manifestHookPaths = manifestTargetPaths("hooks").filter { it.contains("/hooks/") }.toSet()
        val settingsHookPaths =
            listOf(".claude/settings.json", ".codex/hooks.json")
                .flatMap { path ->
                    Regex("""\.(?:claude|codex)/hooks/[A-Za-z0-9._-]+\.sh""")
                        .findAll(repositoryRoot.resolve(path).toFile().readText())
                        .map { it.value }
                        .toList()
                }.toSet()

        assertThat(settingsHookPaths)
            .describedAs("checked-in hook settings must not reference scripts outside the agent-kit manifest")
            .containsExactlyInAnyOrderElementsOf(manifestHookPaths)
    }

    @Test
    fun `hook tool calls use canonical knowledge mcp names`() {
        val knownKnowledgeTools = canonicalKnowledgeToolNames()
        val hookFiles =
            manifestTargetPaths("hooks")
                .plus(manifest["installer"]["path"].asText())
                .distinct()

        val referencedTools =
            hookFiles
                .flatMap { path -> extractToolCallNames(repositoryRoot.resolve(path).toFile().readText()) }
                .toSet()

        assertThat(referencedTools)
            .describedAs("hook and installer tool calls must be names advertised by knowledge-api tests")
            .isSubsetOf(knownKnowledgeTools)

        val legacyNames =
            hookFiles.flatMap { path ->
                Regex("""knowledge_(?:recall|capture_lesson|capture_decision|digest_transcript)""")
                    .findAll(repositoryRoot.resolve(path).toFile().readText())
                    .map { "${path}:${it.value}" }
                    .toList()
            }

        assertThat(legacyNames)
            .describedAs("legacy underscore MCP tool names must not reappear in hooks or installer")
            .isEmpty()

        manifestItems("hooks").forEach { hook ->
            val declaredTools = hook["mcp_tools"]?.elements()?.asSequence()?.map { it.asText() }?.toSet() ?: emptySet()
            assertThat(declaredTools)
                .describedAs("manifest mcp_tools for hook ${hook["name"].asText()}")
                .isSubsetOf(knownKnowledgeTools)
        }
    }

    @Test
    fun `recall injection hooks fall back to fast mode`() {
        listOf(
            ".claude/hooks/kb-user-prompt-recall.sh",
            ".codex/hooks/kb-user-prompt-recall.sh",
        ).forEach { path ->
            val script = repositoryRoot.resolve(path).toFile().readText()
            assertThat(script)
                .describedAs("$path should retry prompt recall in fast mode")
                .contains("""[ "${'$'}{mode}" != "fast" ]""")
                .contains("""call_recall "${'$'}{prompt}" "${'$'}{limit}" fast""")
        }

        val installer = manifest["installer"]["path"].asText()
        val installerScript = repositoryRoot.resolve(installer).toFile().readText()
        assertThat(
            Regex("""call_recall "\$\{(?:prompt|query)}" "\$\{limit}" fast""")
                .findAll(installerScript)
                .count(),
        ).describedAs("installer should generate fast fallback for prompt and edit recall")
            .isGreaterThanOrEqualTo(2)
    }

    private fun repoSkillPaths(): Set<String> =
        listOf(".agents/skills", ".claude/skills")
            .flatMap { base ->
                Files.walk(repositoryRoot.resolve(base)).use { paths ->
                    paths
                        .asSequence()
                        .filter { it.name == "SKILL.md" }
                        .map { relativePath(it) }
                        .toList()
                }
            }.toSet()

    private fun repoHookPaths(): Set<String> =
        listOf(".claude/hooks", ".codex/hooks")
            .flatMap { base ->
                Files.walk(repositoryRoot.resolve(base)).use { paths ->
                    paths
                        .asSequence()
                        .filter { Files.isRegularFile(it) }
                        .map { relativePath(it) }
                        .toList()
                }
            }.toSet()

    private fun skillNamesUnder(base: String): Set<String> =
        Files.list(repositoryRoot.resolve(base)).use { paths ->
            paths
                .asSequence()
                .filter { Files.isDirectory(it) }
                .map { it.name }
                .toSet()
        }

    private fun manifestTargetPaths(section: String): List<String> =
        manifestItems(section)
            .flatMap { item ->
                val targets = item["targets"] ?: return@flatMap emptySequence<String>()
                targets.elements().asSequence().mapNotNull { it["path"]?.asText() }
            }.toList()

    private fun manifestItems(section: String): Sequence<JsonNode> =
        manifest[section]?.elements()?.asSequence() ?: emptySequence()

    private fun collectPinnedPaths(node: JsonNode): List<PinnedPath> {
        val out = mutableListOf<PinnedPath>()

        fun walk(current: JsonNode) {
            when {
                current.isObject -> {
                    val path = current["path"]?.asText()
                    val sha256 = current["sha256"]?.asText()
                    if (!path.isNullOrBlank() && !sha256.isNullOrBlank()) {
                        out += PinnedPath(path = path, sha256 = sha256)
                    }
                    current.fields().asSequence().forEach { walk(it.value) }
                }
                current.isArray -> current.elements().asSequence().forEach { walk(it) }
            }
        }

        walk(node)
        return out
    }

    private fun assertAgentGapIsExplicit(label: String, node: JsonNode) {
        val supportedAgents =
            node["supported_agents"]
                ?.elements()
                ?.asSequence()
                ?.map { it.asText() }
                ?.toSet() ?: emptySet()
        val missingAgents = setOf("claude", "codex") - supportedAgents
        if (missingAgents.isEmpty()) return

        val unsupported = node["unsupported"]
        assertThat(unsupported)
            .describedAs("$label has missing agent support and must carry unsupported reasons")
            .isNotNull()
        missingAgents.forEach { agent ->
            assertThat(unsupported[agent]?.asText())
                .describedAs("$label unsupported reason for $agent")
                .isNotBlank()
        }
    }

    private fun canonicalKnowledgeToolNames(): Set<String> {
        val mcpToolsTest =
            repositoryRoot
                .resolve(
                    "services/knowledge-api/src/test/kotlin/" +
                        "com/jorisjonkers/personalstack/knowledge/mcp/McpToolsTest.kt",
                )
                .toFile()
                .readText()
        return Regex(""""(knowledge\.[a-z_]+)"""")
            .findAll(mcpToolsTest)
            .map { it.groupValues[1] }
            .toSet()
    }

    private fun extractToolCallNames(text: String): List<String> =
        Regex("""["']name["']\s*:\s*["'](knowledge\.[A-Za-z_]+)["']""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()

    private fun relativePath(path: Path): String = repositoryRoot.relativize(path).toString().replace('\\', '/')

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class PinnedPath(
        val path: String,
        val sha256: String,
    )
}
