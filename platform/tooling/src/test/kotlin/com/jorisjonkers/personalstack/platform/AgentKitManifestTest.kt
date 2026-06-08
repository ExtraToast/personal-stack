@file:Suppress("DEPRECATION")

package com.jorisjonkers.personalstack.platform

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Collections
import kotlin.io.path.name
import kotlin.streams.asSequence

class AgentKitManifestTest {
    private val repositoryRoot = RepositoryRootLocator().locate()
    private val manifestPath = repositoryRoot.resolve("platform/agents/kit/manifest.yaml")
    private val jsonMapper = ObjectMapper()
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

        val actualRepoCommandPaths = repoCommandPaths()
        val manifestCommandPaths = manifestTargetPaths("commands").filter { it.contains("/commands/") }.toSet()

        assertThat(manifestCommandPaths)
            .describedAs("every checked-in Claude command must be listed in the agent-kit manifest")
            .containsExactlyInAnyOrderElementsOf(actualRepoCommandPaths)

        val pinnedPaths = collectPinnedPaths(manifest)
        assertThat(pinnedPaths.map { it.path })
            .describedAs("manifest should pin repo settings and installer entrypoint as well as hooks and skills")
            .contains(
                ".claude/settings.json",
                ".codex/hooks.json",
                "services/knowledge-api/src/main/resources/installer/install.sh",
            )

        pinnedPaths.forEach { pinned ->
            val file = rendererSourcePath(pinned.path)
            assertThat(Files.exists(file))
                .describedAs("manifest path exists: ${pinned.path}")
                .isTrue()
            assertThat(sha256(file))
                .describedAs("sha256 for ${pinned.path}")
                .isEqualTo(pinned.sha256)
        }

        manifestItems("commands").forEach { command ->
            val name = command["name"].asText()
            val expectedPath = ".claude/commands/${name}.md"
            assertThat(command["installer"]["target_path"].asText())
                .describedAs("installer target path for command $name")
                .isEqualTo("\${CLAUDE_HOME}/commands/${name}.md")
            assertThat(manifestTargetPaths(command).toList())
                .describedAs("repo target path for command $name")
                .containsExactly(expectedPath)
            assertThat(command["targets"].elements().asSequence().single()["sha256"].asText())
                .describedAs("sha256 for command $name")
                .isEqualTo(sha256(repositoryRoot.resolve(expectedPath)))
        }
    }

    @Test
    fun `shared skills exist for both Claude and Codex unless a gap is explicit`() {
        val codexSkillNames = skillNamesUnder(".agents/skills").filterNot { it.startsWith("speckit-") }.toSet()
        val claudeSkillNames = skillNamesUnder(".claude/skills").filterNot { it.startsWith("speckit-") }.toSet()

        assertThat(codexSkillNames)
            .describedAs("repo-level Codex and Claude skill sets must stay in lockstep")
            .containsExactlyInAnyOrderElementsOf(claudeSkillNames)

        manifestItems("skills")
            .filter { it["name"].asText().startsWith("speckit-") }
            .forEach { skill ->
                val name = skill["name"].asText()
                assertThat(supportedAgents(skill))
                    .describedAs("Codex Speckit skill $name supported agents")
                    .containsExactly("codex")
                assertThat(skill["unsupported"]["claude"]?.asText())
                    .describedAs("Codex Speckit skill $name must point Claude at slash commands")
                    .isNotBlank()
            }

        manifestItems("skills").forEach { skill ->
            assertAgentGapIsExplicit("skill ${skill["name"].asText()}", skill)
        }
        assertAgentGapIsExplicit("installer", manifest["installer"])
    }

    @Test
    fun `Spec Kit Claude commands and Codex skills stay in one to one parity`() {
        val commandNames =
            repoCommandPaths()
                .map { it.substringAfterLast("/speckit.").removeSuffix(".md") }
                .toSet()
        val skillNames =
            repoSkillPaths()
                .mapNotNull { Regex("""^\.agents/skills/speckit-([^/]+)/SKILL\.md$""").matchEntire(it)?.groupValues?.get(1) }
                .toSet()

        assertThat(skillNames)
            .describedAs("each /speckit.<command> must have a matching Codex speckit-<command> skill")
            .containsExactlyInAnyOrderElementsOf(commandNames)
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
                "would write ${claudeHome}/commands/speckit.analyze.md",
                "would write ${claudeHome}/commands/speckit.checklist.md",
                "would write ${claudeHome}/commands/speckit.clarify.md",
                "would write ${claudeHome}/commands/speckit.constitution.md",
                "would write ${claudeHome}/commands/speckit.implement.md",
                "would write ${claudeHome}/commands/speckit.plan.md",
                "would write ${claudeHome}/commands/speckit.specify.md",
                "would write ${claudeHome}/commands/speckit.tasks.md",
                "would write ${claudeHome}/commands/speckit.taskstoissues.md",
                "would write ${claudeHome}/.knowledge-system-allowlist",
                "would write ${codexHome}/hooks/kb-user-prompt-recall.sh",
                "would write ${codexHome}/hooks/pre-tool-use-edit-recall.sh",
                "would write ${codexHome}/hooks/pre-tool-use-git-commit-capture.sh",
                "would write ${codexHome}/hooks/kb-stop-digest.sh",
                "would write ${codexHome}/skills/speckit-analyze/SKILL.md",
                "would write ${codexHome}/skills/speckit-checklist/SKILL.md",
                "would write ${codexHome}/skills/speckit-clarify/SKILL.md",
                "would write ${codexHome}/skills/speckit-constitution/SKILL.md",
                "would write ${codexHome}/skills/speckit-implement/SKILL.md",
                "would write ${codexHome}/skills/speckit-plan/SKILL.md",
                "would write ${codexHome}/skills/speckit-specify/SKILL.md",
                "would write ${codexHome}/skills/speckit-tasks/SKILL.md",
                "would write ${codexHome}/skills/speckit-taskstoissues/SKILL.md",
                "would write ${codexHome}/hooks.json",
                "would write ${codexHome}/.knowledge-system-allowlist",
                "${codexHome}/hooks.json has been written with UserPromptSubmit, PreToolUse,",
            )
        assertThat(Files.exists(codexHome.resolve("hooks.json")))
            .describedAs("dry-run should not write Codex hooks.json")
            .isFalse()
    }

    @Test
    fun `installer agent selection covers Spec Kit commands and Codex skills`() {
        val installer = repositoryRoot.resolve(manifest["installer"]["path"].asText()).toAbsolutePath()

        listOf("claude", "codex", "all").forEach { agent ->
            val claudeHome = tempDir.resolve("agent-${agent}-claude")
            val codexHome = tempDir.resolve("agent-${agent}-codex")
            val environment =
                mapOf(
                    "CLAUDE_CONFIG_DIR" to claudeHome.toString(),
                    "CODEX_HOME" to codexHome.toString(),
                )
            val dryRunResult =
                runProcessWithEnv(
                    environment,
                    "bash",
                    installer.toString(),
                    "--agent",
                    agent,
                    "--dry-run",
                )

            assertThat(dryRunResult.exitCode)
                .describedAs(dryRunResult.stderr)
                .isEqualTo(0)
            assertThat(dryRunResult.stdout.contains("would write ${claudeHome}/commands/speckit.analyze.md"))
                .describedAs("--agent $agent dry-run includes Claude Spec Kit commands only when Claude is selected")
                .isEqualTo(agent != "codex")
            assertThat(dryRunResult.stdout.contains("would write ${codexHome}/skills/speckit-analyze/SKILL.md"))
                .describedAs("--agent $agent dry-run includes Codex Spec Kit skills only when Codex is selected")
                .isEqualTo(agent != "claude")

            val installResult =
                runProcessWithEnv(
                    environment,
                    "bash",
                    installer.toString(),
                    "--agent",
                    agent,
                )

            assertThat(installResult.exitCode)
                .describedAs(installResult.stderr)
                .isEqualTo(0)

            val claudeCommand = claudeHome.resolve("commands/speckit.analyze.md")
            val codexSkill = codexHome.resolve("skills/speckit-analyze/SKILL.md")
            assertThat(Files.exists(claudeCommand))
                .describedAs("--agent $agent writes Claude Spec Kit commands only when Claude is selected")
                .isEqualTo(agent != "codex")
            assertThat(Files.exists(codexSkill))
                .describedAs("--agent $agent writes Codex Spec Kit skills only when Codex is selected")
                .isEqualTo(agent != "claude")

            val uninstallResult =
                runProcessWithEnv(
                    environment,
                    "bash",
                    installer.toString(),
                    "--agent",
                    agent,
                    "--uninstall",
                )

            assertThat(uninstallResult.exitCode)
                .describedAs(uninstallResult.stderr)
                .isEqualTo(0)
            assertThat(Files.exists(claudeCommand))
                .describedAs("--agent $agent uninstall removed Claude Spec Kit commands")
                .isFalse()
            assertThat(Files.exists(codexSkill))
                .describedAs("--agent $agent uninstall removed Codex Spec Kit skills")
                .isFalse()
        }
    }

    @Test
    fun `installer writes parseable Codex hooks and uninstalls managed files`() {
        val installer = repositoryRoot.resolve(manifest["installer"]["path"].asText()).toAbsolutePath()
        val claudeHome = tempDir.resolve("write-claude")
        val codexHome = tempDir.resolve("write-codex")
        val environment =
            mapOf(
                "CLAUDE_CONFIG_DIR" to claudeHome.toString(),
                "CODEX_HOME" to codexHome.toString(),
            )

        val installResult =
            runProcessWithEnv(
                environment,
                "bash",
                installer.toString(),
                "--agent",
                "all",
            )

        assertThat(installResult.exitCode)
            .describedAs(installResult.stderr)
            .isEqualTo(0)
        assertThat(installResult.stdout)
            .contains("knowledge-system installer complete", "agent=all")

        val claudeFiles =
            listOf(
                "hooks/user-prompt-submit-recall.sh",
                "hooks/pre-tool-use-edit-recall.sh",
                "hooks/pre-tool-use-git-commit-capture.sh",
                "hooks/stop-session-digest.sh",
                "commands/speckit.analyze.md",
                "commands/speckit.checklist.md",
                "commands/speckit.clarify.md",
                "commands/speckit.constitution.md",
                "commands/speckit.implement.md",
                "commands/speckit.plan.md",
                "commands/speckit.specify.md",
                "commands/speckit.tasks.md",
                "commands/speckit.taskstoissues.md",
                "skills/topics/SKILL.md",
                "skills/audit/SKILL.md",
                "skills/kb-first/SKILL.md",
                "skills/token-economy/SKILL.md",
                "skills/agent-session-bootstrap/SKILL.md",
                "skills/council/SKILL.md",
                "skills/council/council.py",
                "skills/council/council.toml",
                "skills/council/prompts/planner.md",
                "skills/council/schemas/plan.schema.json",
                ".knowledge-system-allowlist",
                ".knowledge-system-version",
            ).map { claudeHome.resolve(it) }
        val codexFiles =
            listOf(
                "hooks/kb-user-prompt-recall.sh",
                "hooks/pre-tool-use-edit-recall.sh",
                "hooks/pre-tool-use-git-commit-capture.sh",
                "hooks/kb-stop-digest.sh",
                "skills/speckit-analyze/SKILL.md",
                "skills/speckit-checklist/SKILL.md",
                "skills/speckit-clarify/SKILL.md",
                "skills/speckit-constitution/SKILL.md",
                "skills/speckit-implement/SKILL.md",
                "skills/speckit-plan/SKILL.md",
                "skills/speckit-specify/SKILL.md",
                "skills/speckit-tasks/SKILL.md",
                "skills/speckit-taskstoissues/SKILL.md",
                "skills/topics/SKILL.md",
                "skills/audit/SKILL.md",
                "skills/kb-first/SKILL.md",
                "skills/token-economy/SKILL.md",
                "skills/agent-session-bootstrap/SKILL.md",
                "skills/council/SKILL.md",
                "skills/council/council.py",
                "skills/council/council.toml",
                "skills/council/prompts/planner.md",
                "skills/council/schemas/plan.schema.json",
                ".knowledge-system-allowlist",
                ".knowledge-system-version",
                "hooks.json",
            ).map { codexHome.resolve(it) }

        (claudeFiles + codexFiles).forEach { path ->
            assertThat(Files.exists(path))
                .describedAs("installer wrote $path")
                .isTrue()
        }
        listOf(
            claudeHome.resolve("hooks/user-prompt-submit-recall.sh"),
            claudeHome.resolve("hooks/pre-tool-use-edit-recall.sh"),
            claudeHome.resolve("hooks/pre-tool-use-git-commit-capture.sh"),
            claudeHome.resolve("hooks/stop-session-digest.sh"),
            codexHome.resolve("hooks/kb-user-prompt-recall.sh"),
            codexHome.resolve("hooks/pre-tool-use-edit-recall.sh"),
            codexHome.resolve("hooks/pre-tool-use-git-commit-capture.sh"),
            codexHome.resolve("hooks/kb-stop-digest.sh"),
            claudeHome.resolve("skills/council/council.py"),
            codexHome.resolve("skills/council/council.py"),
        ).forEach { hook ->
            assertThat(Files.isExecutable(hook))
                .describedAs("installer made hook executable: $hook")
                .isTrue()
        }

        assertThat(Files.readString(claudeHome.resolve(".knowledge-system-version")))
            .contains("scope=user", "managed:", "hooks/user-prompt-submit-recall.sh", "commands/speckit.analyze.md")
        assertThat(Files.readString(codexHome.resolve(".knowledge-system-version")))
            .contains("agent=codex", "scope=user", "hooks.json", "skills/speckit-analyze/SKILL.md")
        assertThat(Files.exists(tempDir.resolve("write-claude/.specify")))
            .describedAs("user-scope install should not seed .specify under Claude home")
            .isFalse()
        assertThat(Files.exists(tempDir.resolve("write-codex/.specify")))
            .describedAs("user-scope install should not seed .specify under Codex home")
            .isFalse()

        val codexHooks = jsonMapper.readTree(codexHome.resolve("hooks.json").toFile())
        assertThat(hookCommands(codexHooks, "UserPromptSubmit"))
            .containsExactly(codexHome.resolve("hooks/kb-user-prompt-recall.sh").toString())
        assertThat(hookMatchers(codexHooks, "PreToolUse"))
            .containsExactlyInAnyOrder("Edit|Write|apply_patch", "Bash")
        assertThat(hookCommands(codexHooks, "PreToolUse"))
            .containsExactlyInAnyOrder(
                "env KB_AUTO_MCP_HOME=${codexHome} ${codexHome}/hooks/pre-tool-use-edit-recall.sh",
                "env KB_AUTO_MCP_HOME=${codexHome} " +
                    "KB_AUTO_MCP_SOURCE=codex:auto-capture:git-commit " +
                    "KB_AUTO_MCP_CLIENT_NAME=Codex ${codexHome}/hooks/pre-tool-use-git-commit-capture.sh",
            )
        assertThat(hookCommands(codexHooks, "Stop"))
            .containsExactly(codexHome.resolve("hooks/kb-stop-digest.sh").toString())

        val uninstallResult =
            runProcessWithEnv(
                environment,
                "bash",
                installer.toString(),
                "--agent",
                "all",
                "--uninstall",
            )

        assertThat(uninstallResult.exitCode)
            .describedAs(uninstallResult.stderr)
            .isEqualTo(0)
        (claudeFiles + codexFiles).forEach { path ->
            assertThat(Files.exists(path))
                .describedAs("uninstall removed $path")
                .isFalse()
        }
    }

    @Test
    fun `installer project scope writes claude and codex files under project root`() {
        val installer = repositoryRoot.resolve(manifest["installer"]["path"].asText()).toAbsolutePath()
        val projectRoot = tempDir.resolve("project-root").also { Files.createDirectories(it) }
        val ignoredClaudeHome = tempDir.resolve("ignored-claude")
        val ignoredCodexHome = tempDir.resolve("ignored-codex")
        val environment =
            mapOf(
                "AGENT_KIT_PROJECT_ROOT" to projectRoot.toString(),
                "CLAUDE_CONFIG_DIR" to ignoredClaudeHome.toString(),
                "CODEX_HOME" to ignoredCodexHome.toString(),
            )

        val installResult =
            runProcessWithEnv(
                environment,
                "bash",
                installer.toString(),
                "--agent",
                "all",
                "--scope",
                "project",
            )

        assertThat(installResult.exitCode)
            .describedAs(installResult.stderr)
            .isEqualTo(0)
        assertThat(installResult.stdout)
            .contains("knowledge-system installer complete", "agent=all", "scope=project")

        val claudeHome = projectRoot.resolve(".claude")
        val codexHome = projectRoot.resolve(".codex")
        val managedFiles =
            listOf(
                claudeHome.resolve("hooks/user-prompt-submit-recall.sh"),
                claudeHome.resolve("hooks/pre-tool-use-edit-recall.sh"),
                claudeHome.resolve("hooks/pre-tool-use-git-commit-capture.sh"),
                claudeHome.resolve("hooks/stop-session-digest.sh"),
                claudeHome.resolve("commands/speckit.analyze.md"),
                claudeHome.resolve("commands/speckit.checklist.md"),
                claudeHome.resolve("commands/speckit.clarify.md"),
                claudeHome.resolve("commands/speckit.constitution.md"),
                claudeHome.resolve("commands/speckit.implement.md"),
                claudeHome.resolve("commands/speckit.plan.md"),
                claudeHome.resolve("commands/speckit.specify.md"),
                claudeHome.resolve("commands/speckit.tasks.md"),
                claudeHome.resolve("commands/speckit.taskstoissues.md"),
                claudeHome.resolve("skills/kb-first/SKILL.md"),
                claudeHome.resolve(".knowledge-system-allowlist"),
                claudeHome.resolve(".knowledge-system-version"),
                codexHome.resolve("hooks/kb-user-prompt-recall.sh"),
                codexHome.resolve("hooks/pre-tool-use-edit-recall.sh"),
                codexHome.resolve("hooks/pre-tool-use-git-commit-capture.sh"),
                codexHome.resolve("hooks/kb-stop-digest.sh"),
                codexHome.resolve("skills/speckit-analyze/SKILL.md"),
                codexHome.resolve("skills/speckit-checklist/SKILL.md"),
                codexHome.resolve("skills/speckit-clarify/SKILL.md"),
                codexHome.resolve("skills/speckit-constitution/SKILL.md"),
                codexHome.resolve("skills/speckit-implement/SKILL.md"),
                codexHome.resolve("skills/speckit-plan/SKILL.md"),
                codexHome.resolve("skills/speckit-specify/SKILL.md"),
                codexHome.resolve("skills/speckit-tasks/SKILL.md"),
                codexHome.resolve("skills/speckit-taskstoissues/SKILL.md"),
                codexHome.resolve("skills/kb-first/SKILL.md"),
                codexHome.resolve(".knowledge-system-allowlist"),
                codexHome.resolve(".knowledge-system-version"),
                codexHome.resolve("hooks.json"),
            )
        val specifySeedFiles =
            manifest["specify"]["project_seed"]["targets"]
                .elements()
                .asSequence()
                .map { projectRoot.resolve(it["path"].asText()) }
                .toList()

        (managedFiles + specifySeedFiles).forEach { path ->
            assertThat(Files.exists(path))
                .describedAs("project-scope installer wrote $path")
                .isTrue()
        }
        assertThat(Files.isExecutable(projectRoot.resolve(".specify/scripts/bash/check-prerequisites.sh")))
            .describedAs("project-scope install should seed executable Spec Kit scripts")
            .isTrue()
        assertThat(Files.readString(projectRoot.resolve(".specify/memory/constitution.md")).trimEnd())
            .describedAs("project-scope install should seed the repository constitution")
            .isEqualTo(Files.readString(repositoryRoot.resolve(".specify/memory/constitution.md")).trimEnd())
        assertThat(Files.readString(claudeHome.resolve(".knowledge-system-version"))).contains("scope=project")
        assertThat(Files.readString(codexHome.resolve(".knowledge-system-version"))).contains("scope=project")
        assertThat(Files.exists(ignoredClaudeHome))
            .describedAs("project scope should ignore CLAUDE_CONFIG_DIR")
            .isFalse()
        assertThat(Files.exists(ignoredCodexHome))
            .describedAs("project scope should ignore CODEX_HOME")
            .isFalse()

        val uninstallResult =
            runProcessWithEnv(
                environment,
                "bash",
                installer.toString(),
                "--agent",
                "all",
                "--scope",
                "project",
                "--uninstall",
            )

        assertThat(uninstallResult.exitCode)
            .describedAs(uninstallResult.stderr)
            .isEqualTo(0)
        managedFiles.forEach { path ->
            assertThat(Files.exists(path))
                .describedAs("project-scope uninstall removed $path")
                .isFalse()
        }
        specifySeedFiles.forEach { path ->
            assertThat(Files.exists(path))
                .describedAs("project-scope uninstall should preserve project-owned Spec Kit seed $path")
                .isTrue()
        }
    }

    @Test
    fun `installed recall hooks parse payloads dedupe and honor allowlist`() {
        val installer = repositoryRoot.resolve(manifest["installer"]["path"].asText()).toAbsolutePath()
        val claudeHome = tempDir.resolve("hook-claude")
        val codexHome = tempDir.resolve("hook-codex")
        val installEnvironment =
            mapOf(
                "CLAUDE_CONFIG_DIR" to claudeHome.toString(),
                "CODEX_HOME" to codexHome.toString(),
            )
        val installResult =
            runProcessWithEnv(
                installEnvironment,
                "bash",
                installer.toString(),
                "--agent",
                "all",
            )

        assertThat(installResult.exitCode)
            .describedAs(installResult.stderr)
            .isEqualTo(0)

        val curlLog = tempDir.resolve("curl-payloads.jsonl")
        val fakeBin = tempDir.resolve("bin").also { Files.createDirectories(it) }
        fakeBin.resolve("curl").writeExecutable(
            """
            #!/usr/bin/env bash
            while [ "${'$'}#" -gt 0 ]; do
              case "${'$'}1" in
                -d)
                  shift
                  printf '%s\n' "${'$'}1" >> "${'$'}{CURL_CAPTURE_FILE}"
                  ;;
              esac
              shift || true
            done
            cat <<'JSON'
            {"jsonrpc":"2.0","result":{"structuredContent":{"hits":[{"title":"Prior capture","scope":"project:personal-stack","score":0.92,"id":"01H","snippet":"Remember this module"}]}}}
            JSON
            """,
        )

        val hookEnvironment =
            installEnvironment +
                mapOf(
                    "KB_BEARER_TOKEN" to "test-token",
                    "KB_URL" to "http://knowledge.local",
                    "CURL_CAPTURE_FILE" to curlLog.toString(),
                    "PATH" to "${fakeBin}:${System.getenv("PATH")}",
                )

        val promptCurlLog = tempDir.resolve("prompt-curl-payloads.jsonl")
        val promptResult =
            runProcessWithInput(
                hookEnvironment + ("CURL_CAPTURE_FILE" to promptCurlLog.toString()),
                """
                {
                  "user_prompt": "Please improve the personal-stack agent kit memory hooks and installer validation coverage."
                }
                """.trimIndent(),
                claudeHome.resolve("hooks/user-prompt-submit-recall.sh").toAbsolutePath().toString(),
            )

        assertThat(promptResult.exitCode)
            .describedAs(promptResult.stderr)
            .isEqualTo(0)
        assertThat(promptResult.stdout).contains("Knowledge base", "Prior capture", "score 0.92")
        val promptPayload = readCurlPayloads(promptCurlLog).single()
        assertThat(toolArguments(promptPayload)["query"].asText())
            .contains("personal-stack agent kit memory hooks")
        assertThat(toolArguments(promptPayload)["mode"].asText()).isEqualTo("hybrid")
        assertThat(toolArguments(promptPayload)["limit"].asInt()).isEqualTo(3)

        val claudeResult =
            runProcessWithInput(
                hookEnvironment + ("CLAUDE_SESSION_ID" to "claude-edit-session"),
                """{"tool_input":{"file_path":"platform/agents/kit/manifest.yaml"}}""",
                claudeHome.resolve("hooks/pre-tool-use-edit-recall.sh").toAbsolutePath().toString(),
            )

        assertThat(claudeResult.exitCode)
            .describedAs(claudeResult.stderr)
            .isEqualTo(0)
        val claudePayload = readCurlPayloads(curlLog).single()
        assertThat(toolArguments(claudePayload)["query"].asText())
            .contains("manifest.yaml", "platform/agents/kit/manifest.yaml")
        assertThat(toolArguments(claudePayload)["scope"].asText()).isEqualTo("project:personal-stack")
        assertThat(toolArguments(claudePayload)["mode"].asText()).isEqualTo("hybrid")
        assertThat(toolArguments(claudePayload)["limit"].asInt()).isEqualTo(2)
        assertThat(claudeResult.stdout).contains("Related captures for this file", "Prior capture")

        runProcessWithInput(
            hookEnvironment + ("CLAUDE_SESSION_ID" to "claude-edit-session"),
            """{"tool_input":{"file_path":"platform/agents/kit/manifest.yaml"}}""",
            claudeHome.resolve("hooks/pre-tool-use-edit-recall.sh").toAbsolutePath().toString(),
        )
        assertThat(readCurlPayloads(curlLog))
            .describedAs("same session and file should be deduped")
            .hasSize(1)

        val codexResult =
            runProcessWithInput(
                hookEnvironment +
                    mapOf(
                        "KB_AUTO_MCP_HOME" to codexHome.toString(),
                        "CODEX_THREAD_ID" to "codex-edit-session",
                    ),
                """
                {
                  "tool": {
                    "input": {
                      "patch": "*** Begin Patch\n*** Update File: platform/agents/kit/render-agent-kit.py\n@@\n*** End Patch"
                    }
                  }
                }
                """.trimIndent(),
                codexHome.resolve("hooks/pre-tool-use-edit-recall.sh").toAbsolutePath().toString(),
            )

        assertThat(codexResult.exitCode)
            .describedAs(codexResult.stderr)
            .isEqualTo(0)
        assertThat(codexResult.stdout).contains("Related captures for this file", "Prior capture")
        val codexPayload = readCurlPayloads(curlLog).last()
        assertThat(toolArguments(codexPayload)["query"].asText())
            .contains("render-agent-kit.py", "platform/agents/kit/render-agent-kit.py")
        assertThat(toolArguments(codexPayload)["scope"].asText()).isEqualTo("project:personal-stack")

        runProcessWithInput(
            hookEnvironment +
                mapOf(
                    "KB_AUTO_MCP_HOME" to codexHome.toString(),
                    "CODEX_THREAD_ID" to "codex-secret-session",
                ),
            """{"tool_input":{"file_path":".env"}}""",
            codexHome.resolve("hooks/pre-tool-use-edit-recall.sh").toAbsolutePath().toString(),
        )
        assertThat(readCurlPayloads(curlLog))
            .describedAs("allowlisted files should not call the MCP")
            .hasSize(2)
    }

    @Test
    fun `installed capture hooks parse commits and stop digest sessions`() {
        val installer = repositoryRoot.resolve(manifest["installer"]["path"].asText()).toAbsolutePath()
        val claudeHome = tempDir.resolve("capture-claude")
        val codexHome = tempDir.resolve("capture-codex")
        val installEnvironment =
            mapOf(
                "CLAUDE_CONFIG_DIR" to claudeHome.toString(),
                "CODEX_HOME" to codexHome.toString(),
            )
        val installResult =
            runProcessWithEnv(
                installEnvironment,
                "bash",
                installer.toString(),
                "--agent",
                "all",
            )

        assertThat(installResult.exitCode)
            .describedAs(installResult.stderr)
            .isEqualTo(0)

        val fakeBin = tempDir.resolve("capture-bin").also { Files.createDirectories(it) }
        fakeBin.resolve("curl").writeExecutable(
            """
            #!/usr/bin/env bash
            payload=""
            while [ "${'$'}#" -gt 0 ]; do
              case "${'$'}1" in
                -d)
                  shift
                  payload="${'$'}1"
                  printf '%s\n' "${'$'}{payload}" >> "${'$'}{CURL_CAPTURE_FILE}"
                  ;;
              esac
              shift || true
            done
            python3 - "${'$'}payload" <<'PY'
            import json, sys

            payload = json.loads(sys.argv[1])
            name = payload["params"]["name"]
            args = payload["params"].get("arguments", {})

            if name == "knowledge.digest_transcript":
                transcript = args.get("transcript", "")
                is_codex = "Codex stop transcript" in transcript
                print(json.dumps({
                    "jsonrpc": "2.0",
                    "result": {
                        "structuredContent": {
                            "candidates": [{
                                "title": "Codex digest lesson" if is_codex else "Claude digest lesson",
                                "body": "Capture Codex stop lessons without duplicates." if is_codex else "Capture Claude stop lessons without duplicates.",
                                "suggested_topic": "" if is_codex else "agent-tools",
                                "suggested_tags": ["agent-kit", "hooks"],
                            }]
                        }
                    },
                }))
            elif name == "knowledge.recall":
                print(json.dumps({"jsonrpc": "2.0", "result": {"structuredContent": {"hits": []}}}))
            else:
                print(json.dumps({"jsonrpc": "2.0", "result": {"structuredContent": {"id": "01HOOK"}}}))
            PY
            """,
        )

        val hookEnvironment =
            installEnvironment +
                mapOf(
                    "KB_BEARER_TOKEN" to "test-token",
                    "KB_URL" to "http://knowledge.local",
                    "PATH" to "${fakeBin}:${System.getenv("PATH")}",
                )

        val commitCurlLog = tempDir.resolve("commit-curl-payloads.jsonl")
        val commitEnvironment = hookEnvironment + ("CURL_CAPTURE_FILE" to commitCurlLog.toString())
        val claudeCommitResult =
            runProcessWithInput(
                commitEnvironment,
                """{"tool_name":"Bash","tool_input":{"command":"git commit -m \"Add hook capture smoke\""}}""",
                claudeHome.resolve("hooks/pre-tool-use-git-commit-capture.sh").toAbsolutePath().toString(),
            )

        assertThat(claudeCommitResult.exitCode)
            .describedAs(claudeCommitResult.stderr)
            .isEqualTo(0)
        val claudeCommitArgs = toolArguments(readCurlPayloads(commitCurlLog).single())
        assertThat(claudeCommitArgs["title"].asText()).isEqualTo("Add hook capture smoke")
        assertThat(claudeCommitArgs["scope"].asText()).isEqualTo("project:personal-stack")
        assertThat(claudeCommitArgs["source"].asText()).isEqualTo("claude-code:auto-capture:git-commit")
        assertThat(claudeCommitArgs["body"].asText()).contains("Claude PreToolUse", "`git commit` hook")
        assertThat(jsonArrayTexts(claudeCommitArgs["tags"])).containsExactly("auto-capture", "git-commit")

        val codexCommitResult =
            runProcessWithInput(
                commitEnvironment +
                    mapOf(
                        "KB_AUTO_MCP_CLIENT_NAME" to "Codex",
                        "KB_AUTO_MCP_SOURCE" to "codex:auto-capture:git-commit",
                    ),
                """{"tool":{"name":"Bash","input":{"cmd":"git commit -m 'Add Codex commit capture'"}}}""",
                codexHome.resolve("hooks/pre-tool-use-git-commit-capture.sh").toAbsolutePath().toString(),
            )

        assertThat(codexCommitResult.exitCode)
            .describedAs(codexCommitResult.stderr)
            .isEqualTo(0)
        val codexCommitArgs = toolArguments(readCurlPayloads(commitCurlLog).last())
        assertThat(codexCommitArgs["title"].asText()).isEqualTo("Add Codex commit capture")
        assertThat(codexCommitArgs["scope"].asText()).isEqualTo("project:personal-stack")
        assertThat(codexCommitArgs["source"].asText()).isEqualTo("codex:auto-capture:git-commit")
        assertThat(codexCommitArgs["body"].asText()).contains("Codex", "`git commit` hook")

        runProcessWithInput(
            commitEnvironment,
            """{"tool_name":"Bash","tool_input":{"command":"git commit -m \"WIP scratch\""}}""",
            claudeHome.resolve("hooks/pre-tool-use-git-commit-capture.sh").toAbsolutePath().toString(),
        )
        assertThat(readCurlPayloads(commitCurlLog))
            .describedAs("WIP commit messages should not be auto-captured")
            .hasSize(2)

        val claudeTranscript = tempDir.resolve("claude-transcript.jsonl")
        Files.writeString(
            claudeTranscript,
            """{"role":"user","content":"Claude stop transcript should create a durable lesson."}""" + "\n",
        )
        val claudeStopCurlLog = tempDir.resolve("claude-stop-curl-payloads.jsonl")
        val claudeStopEnvironment =
            hookEnvironment +
                mapOf(
                    "CURL_CAPTURE_FILE" to claudeStopCurlLog.toString(),
                    "KB_DIGEST_MAX_CAPTURES" to "1",
                )
        val claudeStopInput =
            """{"session_id":"claude-stop-session","transcript_path":"${claudeTranscript.toAbsolutePath()}"}"""

        val claudeStopResult =
            runProcessWithInput(
                claudeStopEnvironment,
                claudeStopInput,
                claudeHome.resolve("hooks/stop-session-digest.sh").toAbsolutePath().toString(),
            )

        assertThat(claudeStopResult.exitCode)
            .describedAs(claudeStopResult.stderr)
            .isEqualTo(0)
        val claudeStopPayloads = readCurlPayloads(claudeStopCurlLog)
        assertThat(claudeStopPayloads.map { toolName(it) })
            .containsExactly("knowledge.digest_transcript", "knowledge.recall", "knowledge.capture_lesson")
        assertThat(toolArguments(claudeStopPayloads[0])["transcript"].asText())
            .contains("Claude stop transcript")
        assertThat(toolArguments(claudeStopPayloads[0])["max_candidates"].asInt()).isEqualTo(1)
        assertThat(toolArguments(claudeStopPayloads[1])["mode"].asText()).isEqualTo("hybrid")
        val claudeCaptureArgs = toolArguments(claudeStopPayloads[2])
        assertThat(claudeCaptureArgs["title"].asText()).isEqualTo("Claude digest lesson")
        assertThat(claudeCaptureArgs["scope"].asText()).isEqualTo("topic:agent-tools")
        assertThat(claudeCaptureArgs["source"].asText()).isEqualTo("claude-code:auto-digest:claude-stop-session")
        assertThat(claudeCaptureArgs["session_id"].asText()).isEqualTo("claude-stop-session")
        assertThat(jsonArrayTexts(claudeCaptureArgs["tags"])).containsExactly("agent-kit", "hooks")
        assertThat(Files.readString(claudeHome.resolve("state/sessions/claude-stop-session/digest-budget")).trim())
            .isEqualTo("0")

        runProcessWithInput(
            claudeStopEnvironment,
            claudeStopInput,
            claudeHome.resolve("hooks/stop-session-digest.sh").toAbsolutePath().toString(),
        )
        assertThat(readCurlPayloads(claudeStopCurlLog))
            .describedAs("exhausted Stop digest budget should prevent another MCP call")
            .hasSize(3)

        val codexTranscript = tempDir.resolve("codex-transcript.jsonl")
        Files.writeString(
            codexTranscript,
            """{"source":"user","message":"Codex stop transcript should fall back to project scope."}""" + "\n",
        )
        val codexStopCurlLog = tempDir.resolve("codex-stop-curl-payloads.jsonl")
        val codexStopResult =
            runProcessWithInput(
                hookEnvironment +
                    mapOf(
                        "CODEX_HOME" to codexHome.toString(),
                        "CURL_CAPTURE_FILE" to codexStopCurlLog.toString(),
                        "KB_DIGEST_MAX_CAPTURES" to "1",
                    ),
                """{"thread_id":"codex-stop-session","transcriptPath":"${codexTranscript.toAbsolutePath()}"}""",
                codexHome.resolve("hooks/kb-stop-digest.sh").toAbsolutePath().toString(),
            )

        assertThat(codexStopResult.exitCode)
            .describedAs(codexStopResult.stderr)
            .isEqualTo(0)
        val codexStopPayloads = readCurlPayloads(codexStopCurlLog)
        assertThat(codexStopPayloads.map { toolName(it) })
            .containsExactly("knowledge.digest_transcript", "knowledge.recall", "knowledge.capture_lesson")
        val codexCaptureArgs = toolArguments(codexStopPayloads[2])
        assertThat(codexCaptureArgs["title"].asText()).isEqualTo("Codex digest lesson")
        assertThat(codexCaptureArgs["scope"].asText()).isEqualTo("project:personal-stack")
        assertThat(codexCaptureArgs["source"].asText()).isEqualTo("codex:auto-digest:codex-stop-session")
        assertThat(codexCaptureArgs["session_id"].asText()).isEqualTo("codex-stop-session")
        assertThat(Files.readString(codexHome.resolve("state/sessions/codex-stop-session/digest-budget")).trim())
            .isEqualTo("0")
    }

    @Test
    fun `installed hooks stay silent when disabled or unauthenticated`() {
        val installer = repositoryRoot.resolve(manifest["installer"]["path"].asText()).toAbsolutePath()
        val claudeHome = tempDir.resolve("silent-claude")
        val codexHome = tempDir.resolve("silent-codex")
        val installEnvironment =
            mapOf(
                "CLAUDE_CONFIG_DIR" to claudeHome.toString(),
                "CODEX_HOME" to codexHome.toString(),
            )
        val installResult =
            runProcessWithEnv(
                installEnvironment,
                "bash",
                installer.toString(),
                "--agent",
                "all",
            )

        assertThat(installResult.exitCode)
            .describedAs(installResult.stderr)
            .isEqualTo(0)

        val curlLog = tempDir.resolve("silent-curl-calls.log")
        val fakeBin = tempDir.resolve("silent-bin").also { Files.createDirectories(it) }
        fakeBin.resolve("curl").writeExecutable(
            """
            #!/usr/bin/env bash
            printf 'called\n' >> "${'$'}{CURL_CAPTURE_FILE}"
            cat <<'JSON'
            {"jsonrpc":"2.0","result":{"structuredContent":{"hits":[],"candidates":[]}}}
            JSON
            """,
        )

        val claudeTranscript = tempDir.resolve("silent-claude-transcript.jsonl")
        Files.writeString(
            claudeTranscript,
            """{"role":"user","content":"Claude stop transcript should not call MCP when disabled."}""" + "\n",
        )
        val codexTranscript = tempDir.resolve("silent-codex-transcript.jsonl")
        Files.writeString(
            codexTranscript,
            """{"source":"user","message":"Codex stop transcript should not call MCP when disabled."}""" + "\n",
        )

        val hookInvocations =
            listOf(
                HookInvocation(
                    label = "claude prompt recall",
                    input = """{"user_prompt":"Recall should stay silent when disabled."}""",
                    command = claudeHome.resolve("hooks/user-prompt-submit-recall.sh").toAbsolutePath().toString(),
                ),
                HookInvocation(
                    label = "codex prompt recall",
                    input = """{"user_prompt":"Recall should stay silent when disabled."}""",
                    command = codexHome.resolve("hooks/kb-user-prompt-recall.sh").toAbsolutePath().toString(),
                ),
                HookInvocation(
                    label = "claude edit recall",
                    input = """{"tool_input":{"file_path":"platform/agents/kit/manifest.yaml"}}""",
                    command = claudeHome.resolve("hooks/pre-tool-use-edit-recall.sh").toAbsolutePath().toString(),
                ),
                HookInvocation(
                    label = "codex edit recall",
                    input = """{"tool_input":{"file_path":"platform/agents/kit/manifest.yaml"}}""",
                    command = codexHome.resolve("hooks/pre-tool-use-edit-recall.sh").toAbsolutePath().toString(),
                    environment = mapOf("KB_AUTO_MCP_HOME" to codexHome.toString()),
                ),
                HookInvocation(
                    label = "claude git capture",
                    input = """{"tool_name":"Bash","tool_input":{"command":"git commit -m \"Capture hook silence\""}}""",
                    command = claudeHome.resolve("hooks/pre-tool-use-git-commit-capture.sh").toAbsolutePath().toString(),
                ),
                HookInvocation(
                    label = "codex git capture",
                    input = """{"tool":{"name":"Bash","input":{"cmd":"git commit -m 'Capture Codex silence'"}}}""",
                    command = codexHome.resolve("hooks/pre-tool-use-git-commit-capture.sh").toAbsolutePath().toString(),
                    environment =
                        mapOf(
                            "KB_AUTO_MCP_CLIENT_NAME" to "Codex",
                            "KB_AUTO_MCP_SOURCE" to "codex:auto-capture:git-commit",
                        ),
                ),
                HookInvocation(
                    label = "claude stop digest",
                    input = """{"session_id":"silent-claude","transcript_path":"${claudeTranscript.toAbsolutePath()}"}""",
                    command = claudeHome.resolve("hooks/stop-session-digest.sh").toAbsolutePath().toString(),
                ),
                HookInvocation(
                    label = "codex stop digest",
                    input = """{"thread_id":"silent-codex","transcriptPath":"${codexTranscript.toAbsolutePath()}"}""",
                    command = codexHome.resolve("hooks/kb-stop-digest.sh").toAbsolutePath().toString(),
                    environment = mapOf("CODEX_HOME" to codexHome.toString()),
                ),
            )
        val hookEnvironment =
            installEnvironment +
                mapOf(
                    "KB_URL" to "http://knowledge.local",
                    "CURL_CAPTURE_FILE" to curlLog.toString(),
                    "PATH" to "${fakeBin}:${System.getenv("PATH")}",
                )

        hookInvocations.forEach { invocation ->
            val result =
                runProcessWithInput(
                    hookEnvironment +
                        invocation.environment +
                        mapOf(
                            "KB_BEARER_TOKEN" to "test-token",
                            "KB_AUTO_MCP_DISABLED" to "1",
                        ),
                    invocation.input,
                    invocation.command,
                )
            assertThat(result.exitCode)
                .describedAs("${invocation.label}: ${result.stderr}")
                .isEqualTo(0)
        }
        assertThat(Files.exists(curlLog))
            .describedAs("panic switch should prevent every installed hook from calling curl")
            .isFalse()

        Files.deleteIfExists(curlLog)
        hookInvocations.forEach { invocation ->
            val result =
                runProcessWithInput(
                    hookEnvironment +
                        invocation.environment +
                        ("KB_BEARER_TOKEN" to ""),
                    invocation.input,
                    invocation.command,
                )
            assertThat(result.exitCode)
                .describedAs("${invocation.label}: ${result.stderr}")
                .isEqualTo(0)
        }
        assertThat(Files.exists(curlLog))
            .describedAs("missing bearer token should prevent every installed hook from calling curl")
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
        if (checkResult.exitCode == 0) {
            assertThat(checkResult.stdout).contains("agent kit render check passed")
        } else {
            val missingGeneratedSkillLines =
                (checkResult.stdout + checkResult.stderr)
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .toList()
            assertThat(missingGeneratedSkillLines)
                .describedAs(checkResult.stderr)
                .allSatisfy { line ->
                    assertThat(line).matches("""missing: \.agents/skills/speckit-[^/]+/SKILL\.md""")
                }
            assertThat(missingGeneratedSkillLines)
                .describedAs("renderer check drift should be limited to generated Codex Spec Kit skill outputs")
                .hasSize(
                    repoSkillPaths()
                        .count { it.startsWith(".agents/skills/speckit-") },
                )
        }

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
                .isEqualTo(Files.readAllBytes(rendererSourcePath(path)))
        }
    }

    @Test
    fun `portability runbook documents install scope export restore and compatibility matrix`() {
        val runbook = repositoryRoot.resolve("platform/agents/kit/PORTABILITY.md").toFile().readText()

        assertThat(runbook)
            .contains(
                "--scope user",
                "--scope project",
                "knowledge-vault",
                "Postgres logical backup",
                "Restore Order",
                "Compatibility Matrix",
                "Claude Code",
                "Codex",
                "knowledge.recall",
            )
    }

    @Test
    fun `agent kit doctor reports static health and skipped live checks`() {
        val renderer = repositoryRoot.resolve(manifest["renderer"]["script_path"].asText())
        val home = tempDir.resolve("doctor-home")

        val result =
            runProcessWithEnv(
                mapOf(
                    "HOME" to home.toString(),
                    "CLAUDE_CONFIG_DIR" to home.resolve(".claude").toString(),
                    "CODEX_HOME" to home.resolve(".codex").toString(),
                    "KB_URL" to "",
                    "KB_BEARER_TOKEN" to "",
                ),
                renderer.toAbsolutePath().toString(),
                "--doctor",
            )

        assertThat(result.exitCode)
            .describedAs(result.stderr)
            .isEqualTo(0)
        assertThat(result.stdout)
            .contains(
                "agent kit doctor",
                "ok   render: generated files match templates",
                "ok   manifest: kit manifest version 2",
                "ok   mcp-profiles: 5 synchronized profiles; minimal 2 Claude/2 Codex servers; full-diagnostic 7 Claude/7 Codex servers",
                "warn claude-install: manifest missing",
                "warn codex-install: manifest missing",
                "warn kb-live: KB_URL is not set; live MCP probe skipped",
                "summary: 3 ok, 3 warn, 0 fail",
            )
    }

    @Test
    fun `agent kit doctor validates installed manifest versions when expected version is set`() {
        val renderer = repositoryRoot.resolve(manifest["renderer"]["script_path"].asText())
        val home = tempDir.resolve("doctor-installed")
        val claudeHome = home.resolve(".claude")
        val codexHome = home.resolve(".codex")
        Files.createDirectories(claudeHome)
        Files.createDirectories(codexHome)
        Files.writeString(claudeHome.resolve(".knowledge-system-version"), "version=current\n")
        Files.writeString(codexHome.resolve(".knowledge-system-version"), "version=current\nagent=codex\n")

        val result =
            runProcessWithEnv(
                mapOf(
                    "HOME" to home.toString(),
                    "CLAUDE_CONFIG_DIR" to claudeHome.toString(),
                    "CODEX_HOME" to codexHome.toString(),
                    "AGENT_KIT_EXPECTED_VERSION" to "current",
                    "KB_URL" to "",
                    "KB_BEARER_TOKEN" to "",
                ),
                renderer.toAbsolutePath().toString(),
                "--doctor",
            )

        assertThat(result.exitCode)
            .describedAs(result.stderr)
            .isEqualTo(0)
        assertThat(result.stdout)
            .contains(
                "ok   manifest: kit manifest version 2",
                "ok   mcp-profiles: 5 synchronized profiles; minimal 2 Claude/2 Codex servers; full-diagnostic 7 Claude/7 Codex servers",
                "ok   claude-install: manifest version current, expected current",
                "ok   codex-install: manifest version current, expected current",
                "summary: 5 ok, 1 warn, 0 fail",
            )
    }

    @Test
    fun `agent kit doctor can require live kb credentials`() {
        val renderer = repositoryRoot.resolve(manifest["renderer"]["script_path"].asText())
        val home = tempDir.resolve("doctor-live")

        val result =
            runProcessWithEnv(
                mapOf(
                    "HOME" to home.toString(),
                    "CLAUDE_CONFIG_DIR" to home.resolve(".claude").toString(),
                    "CODEX_HOME" to home.resolve(".codex").toString(),
                    "KB_URL" to "http://knowledge.local",
                    "KB_BEARER_TOKEN" to "",
                ),
                renderer.toAbsolutePath().toString(),
                "--doctor",
                "--require-live-kb",
            )

        assertThat(result.exitCode)
            .describedAs(result.stdout)
            .isEqualTo(1)
        assertThat(result.stdout)
            .contains(
                "fail kb-live: KB_BEARER_TOKEN is not set; live MCP probe skipped",
                "summary: 3 ok, 2 warn, 1 fail",
            )
    }

    @Test
    fun `agent kit doctor probes tools list and fast recall when live kb credentials exist`() {
        val renderer = repositoryRoot.resolve(manifest["renderer"]["script_path"].asText())
        val home = tempDir.resolve("doctor-live-ok")
        val payloads = Collections.synchronizedList(mutableListOf<JsonNode>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { exchange ->
            val payload = jsonMapper.readTree(exchange.requestBody.readAllBytes())
            payloads.add(payload)
            val response =
                when (payload["method"].asText()) {
                    "tools/list" ->
                        """
                        {"jsonrpc":"2.0","id":"agent-kit-doctor-tools","result":{"tools":[{"name":"knowledge.recall"},{"name":"knowledge.capture_lesson"}]}}
                        """.trimIndent()
                    "tools/call" ->
                        """
                        {"jsonrpc":"2.0","id":"agent-kit-doctor-recall","result":{"structuredContent":{"hits":[{"id":"01H","title":"Prior note"}]}}}
                        """.trimIndent()
                    else ->
                        """{"jsonrpc":"2.0","error":{"code":-32601,"message":"method not found"}}"""
                }
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()

        try {
            val result =
                runProcessWithEnv(
                    mapOf(
                        "HOME" to home.toString(),
                        "CLAUDE_CONFIG_DIR" to home.resolve(".claude").toString(),
                        "CODEX_HOME" to home.resolve(".codex").toString(),
                        "KB_URL" to "http://127.0.0.1:${server.address.port}",
                        "KB_BEARER_TOKEN" to "test-token",
                    ),
                    renderer.toAbsolutePath().toString(),
                    "--doctor",
                )

            assertThat(result.exitCode)
                .describedAs(result.stderr)
                .isEqualTo(0)
            assertThat(result.stdout)
                .contains(
                    "ok   kb-live: reachable at http://127.0.0.1:${server.address.port}/mcp with 2 tools; " +
                        "fast recall returned 1 hits",
                    "summary: 4 ok, 2 warn, 0 fail",
                )
            assertThat(payloads.map { it["method"].asText() })
                .containsExactly("tools/list", "tools/call")

            val recallPayload = payloads[1]
            assertThat(recallPayload["params"]["name"].asText()).isEqualTo("knowledge.recall")
            val arguments = recallPayload["params"]["arguments"]
            assertThat(arguments["query"].asText()).contains("agent kit doctor")
            assertThat(arguments["scope"].asText()).isEqualTo("project:personal-stack")
            assertThat(arguments["mode"].asText()).isEqualTo("fast")
            assertThat(arguments["limit"].asInt()).isEqualTo(1)
        } finally {
            server.stop(0)
        }
    }

    private fun repoSkillPaths(): Set<String> =
        listOf(".agents/skills", ".claude/skills")
            .flatMap { base ->
                filesUnder(rendererSourceRoot(base))
                    .filter { it.name == "SKILL.md" }
                    .map { base + "/" + relativePath(rendererSourceRoot(base), it) }
            }.toSet()

    private fun repoCommandPaths(): Set<String> =
        filesUnder(rendererSourceRoot(".claude/commands"))
            .filter { it.name.startsWith("speckit.") && it.name.endsWith(".md") }
            .map { ".claude/commands/" + relativePath(rendererSourceRoot(".claude/commands"), it) }
            .toSet()

    private fun repoHookPaths(): Set<String> =
        listOf(".claude/hooks", ".codex/hooks")
            .flatMap { base ->
                filesUnder(rendererSourceRoot(base))
                    .map { base + "/" + relativePath(rendererSourceRoot(base), it) }
            }.toSet()

    private fun skillNamesUnder(base: String): Set<String> =
        Files.list(rendererSourceRoot(base)).use { paths ->
            paths
                .asSequence()
                .filter { Files.isDirectory(it) }
                .map { it.name }
                .toSet()
        }

    private fun manifestTargetPaths(section: String): List<String> =
        manifestItems(section)
            .flatMap { manifestTargetPaths(it) }
            .toList()

    private fun manifestTargetPaths(item: JsonNode): Sequence<String> {
        val targets = item["targets"] ?: return emptySequence()
        return targets.elements().asSequence().mapNotNull { it["path"]?.asText() }
    }

    private fun manifestItems(section: String): Sequence<JsonNode> =
        manifest[section]?.elements()?.asSequence() ?: emptySequence()

    private fun supportedAgents(node: JsonNode): Set<String> =
        node["supported_agents"]
            ?.elements()
            ?.asSequence()
            ?.map { it.asText() }
            ?.toSet() ?: emptySet()

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
        val missingAgents = setOf("claude", "codex") - supportedAgents(node)
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

    private fun hookMatchers(
        settings: JsonNode,
        event: String,
    ): List<String> =
        settings["hooks"][event]
            .elements()
            .asSequence()
            .mapNotNull { it["matcher"]?.asText() }
            .toList()

    private fun hookCommands(
        settings: JsonNode,
        event: String,
    ): List<String> =
        settings["hooks"][event]
            .elements()
            .asSequence()
            .flatMap { group -> group["hooks"].elements().asSequence() }
            .map { it["command"].asText() }
            .toList()

    private fun readCurlPayloads(path: Path): List<JsonNode> =
        if (!Files.exists(path)) {
            emptyList()
        } else {
            Files.readAllLines(path)
                .filter { it.isNotBlank() }
                .map { jsonMapper.readTree(it) }
        }

    private fun toolArguments(payload: JsonNode): JsonNode = payload["params"]["arguments"]

    private fun toolName(payload: JsonNode): String = payload["params"]["name"].asText()

    private fun jsonArrayTexts(node: JsonNode): List<String> =
        node.elements().asSequence().map { it.asText() }.toList()

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

    private fun rendererSourceRoot(base: String): Path {
        val templateRoot = repositoryRoot.resolve(manifest["renderer"]["template_root"].asText()).resolve(base)
        return if (Files.exists(templateRoot)) templateRoot else repositoryRoot.resolve(base)
    }

    private fun rendererSourcePath(path: String): Path {
        val livePath = repositoryRoot.resolve(path)
        if (Files.exists(livePath)) return livePath
        val templatePath = repositoryRoot.resolve(manifest["renderer"]["template_root"].asText()).resolve(path)
        return if (Files.exists(templatePath)) templatePath else livePath
    }

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

    private fun runProcessWithInput(
        environment: Map<String, String>,
        input: String,
        vararg command: String,
    ): AgentKitProcessResult {
        val process =
            ProcessBuilder(command.toList())
                .directory(repositoryRoot.toFile())
                .also { it.environment().putAll(environment) }
                .start()
        process.outputStream.use { stdin ->
            stdin.write(input.toByteArray())
        }
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

    private fun Path.writeExecutable(contents: String): String {
        Files.writeString(this, contents.trimIndent() + "\n")
        toFile().setExecutable(true)
        return toAbsolutePath().toString()
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

    private data class HookInvocation(
        val label: String,
        val input: String,
        val command: String,
        val environment: Map<String, String> = emptyMap(),
    )
}
