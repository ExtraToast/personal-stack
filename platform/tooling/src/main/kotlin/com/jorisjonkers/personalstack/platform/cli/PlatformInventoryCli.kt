package com.jorisjonkers.personalstack.platform.cli

import com.jorisjonkers.personalstack.platform.RepositoryRootLocator
import com.jorisjonkers.personalstack.platform.inventory.NodeInfo
import com.jorisjonkers.personalstack.platform.inventory.PlatformFleetLoader
import java.io.Writer
import java.nio.file.Path
import kotlin.system.exitProcess

class PlatformInventoryCli(
    private val repositoryRoot: Path = RepositoryRootLocator().locate(),
    private val fleetLoader: PlatformFleetLoader = PlatformFleetLoader(),
    private val stdout: Writer = System.out.writer(),
    private val stderr: Writer = System.err.writer(),
) {
    fun run(vararg args: String): Int {
        if (args.isEmpty()) {
            return fail("Usage: show-host-env <node-name>")
        }

        return when (args.first()) {
            "show-host-env" -> showHostEnv(args.drop(1))
            else -> fail("Unknown command: ${args.first()}")
        }
    }

    private fun showHostEnv(args: List<String>): Int {
        if (args.size != 1) {
            return fail("Usage: show-host-env <node-name>")
        }

        val nodeName = args.single()
        val fleet = fleetLoader.load(repositoryRoot.resolve("platform/inventory/fleet.yaml"))
        val node = fleet.nodes[nodeName] ?: return fail("Unknown node: $nodeName")

        stdout.writeLine("NODE_NAME", nodeName)
        stdout.writeLine("NODE_STATUS", node.status)
        stdout.writeLine("NODE_SITE", node.site)
        stdout.writeLine("NODE_ARCH", node.arch)
        stdout.writeLine("NIX_SYSTEM", node.toNixSystem())
        stdout.writeLine("HAS_SSH", (node.ssh != null).toString())
        stdout.writeLine("SSH_HOST", node.ssh?.host.orEmpty())
        stdout.writeLine("SSH_USER", node.ssh?.user.orEmpty())
        stdout.writeLine("SSH_PORT", node.ssh?.port?.toString().orEmpty())
        stdout.writeLine("IS_CONTROL_PLANE", ("k3s-control-plane" in node.targetRoles).toString())
        stdout.writeLine("IS_WORKER", ("k3s-worker" in node.targetRoles).toString())
        stdout.writeLine("IS_UTILITY_HOST", ("utility-host" in node.targetRoles).toString())
        stdout.writeLine("HAS_NVIDIA", ("nvidia" in node.capabilities).toString())
        stdout.flush()
        return 0
    }

    private fun fail(message: String): Int {
        stderr.appendLine(message)
        stderr.flush()
        return 1
    }
}

private fun Writer.writeLine(key: String, value: String) {
    append(key)
    append('=')
    appendLine(value)
}

private fun NodeInfo.toNixSystem(): String =
    when (arch) {
        "amd64" -> "x86_64-linux"
        "arm64" -> "aarch64-linux"
        else -> error("Unsupported arch $arch")
    }

fun main(args: Array<String>) {
    exitProcess(PlatformInventoryCli().run(*args))
}
