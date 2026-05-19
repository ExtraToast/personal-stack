package com.jorisjonkers.personalstack.agentgateway.git

import com.jorisjonkers.personalstack.agentgateway.config.GatewayProperties
import com.jorisjonkers.personalstack.agentgateway.process.ProcessRunner
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.Path

/**
 * Wraps git + gh for the runner. The deploy key lives at
 * `agent-gateway.git.deploy-key-dir/private_key`; this client
 * materialises it into a private file with 0600 and exports
 * GIT_SSH_COMMAND so every clone/push uses it without polluting
 * ~/.ssh.
 *
 * `gh` is used for the PR open step because issuing a PAT for the
 * agent would be a wider blast radius than the per-repo deploy key
 * needs to cover — gh authenticates via a GH_TOKEN env var that
 * assistant-api injects per-Pod from a scoped fine-grained token.
 */
@Component
class GitClient(
    private val runner: ProcessRunner,
    private val props: GatewayProperties,
) {
    private val log = LoggerFactory.getLogger(GitClient::class.java)

    fun clone(
        repoUrl: String,
        intoDir: String,
        branch: String? = null,
    ): String {
        val argv =
            mutableListOf("git", "clone", "--depth", "50").apply {
                if (branch != null) {
                    add("--branch")
                    add(branch)
                }
                add(repoUrl)
                add(intoDir)
            }
        runner.run(argv, env = gitEnv(), timeoutSeconds = 300)
        return intoDir
    }

    fun checkoutNewBranch(
        repoDir: String,
        branch: String,
    ) {
        runner.run(
            listOf("git", "checkout", "-b", branch),
            cwd = File(repoDir),
        )
    }

    fun push(
        repoDir: String,
        remote: String = "origin",
        branch: String? = null,
    ): String {
        val argv =
            mutableListOf("git", "push", "-u", remote).apply {
                if (branch != null) add(branch) else add("HEAD")
            }
        return runner.run(argv, cwd = File(repoDir), env = gitEnv(), timeoutSeconds = 300).combined
    }

    fun openPr(
        repoDir: String,
        title: String,
        body: String,
        base: String = "main",
    ): String {
        val argv =
            listOf(
                "gh",
                "pr",
                "create",
                "--title",
                title,
                "--body",
                body,
                "--base",
                base,
            )
        return runner.run(argv, cwd = File(repoDir), env = ghEnv(), timeoutSeconds = 120).stdout.trim()
    }

    fun currentBranch(repoDir: String): String =
        runner
            .run(
                listOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
                cwd = File(repoDir),
            ).stdout
            .trim()

    private fun gitEnv(): Map<String, String> {
        val key = ensureDeployKey()
        val known = Path(props.git.deployKeyDir).resolve("known_hosts").toFile()
        val sshOpts =
            buildString {
                append("ssh -i ${key.toAbsolutePath()} -o IdentitiesOnly=yes")
                if (known.exists()) append(" -o UserKnownHostsFile=${known.absolutePath}")
            }
        return mapOf("GIT_SSH_COMMAND" to sshOpts)
    }

    private fun ghEnv(): Map<String, String> {
        val token = System.getenv("GH_TOKEN") ?: System.getenv("GITHUB_TOKEN") ?: ""
        return mapOf("GH_TOKEN" to token)
    }

    private fun ensureDeployKey(): Path {
        val source = Path(props.git.deployKeyDir).resolve("private_key")
        if (!Files.exists(source)) {
            error("deploy key missing at $source — assistant-api should have projected it")
        }
        // Stash in /tmp with 0600 — Secret-mounted files are owned by
        // root and have permissive default modes that openssh refuses.
        val target = Path("/tmp/agent-deploy-key")
        if (!Files.exists(target) || Files.size(target) != Files.size(source)) {
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        runCatching {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"))
        }.onFailure { log.warn("could not chmod 0600 deploy key: {}", it.message) }
        return target
    }
}
