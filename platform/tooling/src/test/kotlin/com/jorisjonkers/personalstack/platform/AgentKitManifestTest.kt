@file:Suppress("DEPRECATION")

package com.jorisjonkers.personalstack.platform

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.name
import kotlin.streams.asSequence

class AgentKitManifestTest {
    private val repositoryRoot = RepositoryRootLocator().locate()
    private val manifestPath = repositoryRoot.resolve("platform/agents/kit/manifest.yaml")
    private val manifest = ObjectMapper(YAMLFactory()).readTree(manifestPath.toFile())

    @TempDir
    lateinit var tempDir: Path

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
    fun `installer dry-run covers Claude and Codex managed surfaces`() {
        val installer = repositoryRoot.resolve(manifest["installer"]["path"].asText()).toAbsolutePath()
        val claudeHome = tempDir.resolve("installer-claude")
        val codexHome = tempDir.resolve("installer-codex")

        val result =
            runProcessWithEnv(
                mapOf(
                    "CLAUDE_CONFIG_DIR" to claudeHome.toString(),
                    "CODEX_HOME" to codexHome.toString(),
                ),
                "bash",
                installer.toString(),
                "--agent",
                "all",
                "--dry-run",
            )

        assertThat(result.exitCode)
            .describedAs(result.stderr)
            .isEqualTo(0)
        assertThat(result.stdout)
            .contains(
                "would write ${claudeHome}/hooks/user-prompt-submit-recall.sh",
                "would write ${claudeHome}/hooks/pre-tool-use-edit-recall.sh",
                "would write ${claudeHome}/hooks/pre-tool-use-git-commit-capture.sh",
                "would write ${claudeHome}/hooks/stop-session-digest.sh",
                "would write ${claudeHome}/.knowledge-system-allowlist",
                "would write ${codexHome}/hooks/kb-user-prompt-recall.sh",
                "would write ${codexHome}/hooks/pre-tool-use-edit-recall.sh",
                "would write ${codexHome}/hooks/pre-tool-use-git-commit-capture.sh",
                "would write ${codexHome}/hooks/kb-stop-digest.sh",
                "would write ${codexHome}/hooks.json",
                "would write ${codexHome}/.knowledge-system-allowlist",
                "${codexHome}/hooks.json has been written with UserPromptSubmit, PreToolUse,",
            )
        assertThat(Files.exists(codexHome.resolve("hooks.json")))
            .describedAs("dry-run should not write Codex hooks.json")
            .isFalse()
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

        listOf(
            ".claude/hooks/pre-tool-use-edit-recall.sh",
            ".codex/hooks/pre-tool-use-edit-recall.sh",
        ).forEach { path ->
            val editHook = repositoryRoot.resolve(path).toFile().readText()
            assertThat(editHook)
                .describedAs("$path should retry edit recall in fast mode with repo scope")
                .contains("""[ "${'$'}{mode}" != "fast" ]""")
                .contains("call_recall \"${'$'}{query}\" \"${'$'}{limit}\" fast \"${'$'}{scope}\"")
                .contains("""args["scope"] = sys.argv[4]""")
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

    @Test
    fun `renderer templates are declared in the manifest`() {
        val templatePaths = rendererTemplatePaths()
        val managedPathList = rendererManagedPathList()
        val managedPaths = managedPathList.toSet()
        val pinnedPaths = collectPinnedPaths(manifest).map { it.path }.toSet()

        assertThat(managedPathList)
            .describedAs("renderer managed paths must not contain duplicates")
            .doesNotHaveDuplicates()
        assertThat(templatePaths)
            .describedAs("renderer templates must match manifest managed paths exactly")
            .containsExactlyInAnyOrderElementsOf(managedPaths)
        assertThat(pinnedPaths)
            .describedAs("renderer-managed live files must still be pinned by manifest sha256")
            .containsAll(managedPaths)
    }

    @Test
    fun `renderer include partials are declared and resolvable`() {
        val declaredPartials =
            manifest["renderer"]["include_templates"]
                ?.elements()
                ?.asSequence()
                ?.map { it.asText() }
                ?.toSet() ?: emptySet()
        val referencedPartials = rendererIncludeTemplatePaths()

        assertThat(declaredPartials)
            .describedAs("renderer include partials must be explicit manifest inventory")
            .containsExactlyInAnyOrderElementsOf(referencedPartials)

        declaredPartials.forEach { path ->
            assertThat(Files.exists(repositoryRoot.resolve(path)))
                .describedAs("renderer include partial exists: $path")
                .isTrue()
        }
    }

    @Test
    fun `renderer check passes and can render templates to a temp directory`() {
        val renderer = repositoryRoot.resolve(manifest["renderer"]["script_path"].asText())
        assertThat(Files.isExecutable(renderer))
            .describedAs("agent-kit renderer should be directly executable")
            .isTrue()

        val checkResult = runProcess(renderer.toAbsolutePath().toString(), "--check")
        assertThat(checkResult.exitCode)
            .describedAs(checkResult.stderr)
            .isEqualTo(0)
        assertThat(checkResult.stdout).contains("agent kit render check passed")

        val outputDir = tempDir.resolve("agent-kit-render")
        val renderResult =
            runProcess(
                renderer.toAbsolutePath().toString(),
                "--output",
                outputDir.toAbsolutePath().toString(),
            )
        assertThat(renderResult.exitCode)
            .describedAs(renderResult.stderr)
            .isEqualTo(0)

        rendererManagedPaths().forEach { path ->
            assertThat(Files.readAllBytes(outputDir.resolve(path)))
                .describedAs("rendered output for $path")
                .isEqualTo(Files.readAllBytes(repositoryRoot.resolve(path)))
        }
    }

    private fun repoSkillPaths(): Set<String> =
        listOf(".agents/skills", ".claude/skills")
            .flatMap { base ->
                filesUnder(repositoryRoot.resolve(base))
                    .filter { it.name == "SKILL.md" }
                    .map { relativePath(repositoryRoot, it) }
            }.toSet()

    private fun repoHookPaths(): Set<String> =
        listOf(".claude/hooks", ".codex/hooks")
            .flatMap { base ->
                filesUnder(repositoryRoot.resolve(base))
                    .map { relativePath(repositoryRoot, it) }
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

    private fun rendererManagedPathList(): List<String> =
        manifest["renderer"]["managed_paths"]
            .elements()
            .asSequence()
            .map { it.asText() }
            .toList()

    private fun rendererManagedPaths(): Set<String> = rendererManagedPathList().toSet()

    private fun rendererTemplatePaths(): Set<String> {
        val templateRoot = repositoryRoot.resolve(manifest["renderer"]["template_root"].asText())
        val repoTemplatePaths = filesUnder(templateRoot).map { relativePath(templateRoot, it) }
        val extraTemplatePaths =
            manifest["renderer"]["extra_templates"]
                ?.elements()
                ?.asSequence()
                ?.map { mapping ->
                    val source = repositoryRoot.resolve(mapping["source_path"].asText())
                    assertThat(Files.exists(source))
                        .describedAs("extra renderer template exists: ${mapping["source_path"].asText()}")
                        .isTrue()
                    mapping["destination_path"].asText()
                }?.toList() ?: emptyList()

        return (repoTemplatePaths + extraTemplatePaths).toSet()
    }

    private fun rendererIncludeTemplatePaths(): Set<String> =
        manifest["renderer"]["extra_templates"]
            ?.elements()
            ?.asSequence()
            ?.flatMap { mapping ->
                val source = repositoryRoot.resolve(mapping["source_path"].asText())
                val templateRoot = source.parent
                Regex("""^# @agent-kit-include ([A-Za-z0-9_./-]+)$""", RegexOption.MULTILINE)
                    .findAll(source.toFile().readText())
                    .map { relativePath(repositoryRoot, templateRoot.resolve(it.groupValues[1])) }
            }?.toSet() ?: emptySet()

    private fun filesUnder(root: Path): List<Path> =
        Files.walk(root).use { paths ->
            paths
                .asSequence()
                .filter { Files.isRegularFile(it) }
                .toList()
        }

    private fun runProcess(vararg command: String): AgentKitProcessResult {
        return runProcessWithEnv(emptyMap(), *command)
    }

    private fun runProcessWithEnv(
        environment: Map<String, String>,
        vararg command: String,
    ): AgentKitProcessResult {
        val process =
            ProcessBuilder(command.toList())
                .directory(repositoryRoot.toFile())
                .also { it.environment().putAll(environment) }
                .start()
        val stdout = process.inputStream.readAllBytes().decodeToString()
        val stderr = process.errorStream.readAllBytes().decodeToString()
        return AgentKitProcessResult(
            exitCode = process.waitFor(),
            stdout = stdout,
            stderr = stderr,
        )
    }

    private fun relativePath(root: Path, path: Path): String = root.relativize(path).toString().replace('\\', '/')

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class PinnedPath(
        val path: String,
        val sha256: String,
    )

    private data class AgentKitProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
