package com.jorisjonkers.personalstack.platform.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.jorisjonkers.personalstack.platform.RepositoryRootLocator
import com.jorisjonkers.personalstack.platform.inventory.NodeInfo
import com.jorisjonkers.personalstack.platform.inventory.PlatformFleet
import com.jorisjonkers.personalstack.platform.inventory.PlatformFleetLoader
import java.io.Writer
import java.nio.file.Path
import kotlin.system.exitProcess

class PlatformInventoryCli(
    private val repositoryRoot: Path = RepositoryRootLocator().locate(),
    private val fleetLoader: PlatformFleetLoader = PlatformFleetLoader(),
    private val stdout: Writer = System.out.writer(),
    private val stderr: Writer = System.err.writer(),
    private val yamlMapper: ObjectMapper =
        ObjectMapper(YAMLFactory()).registerModule(
            KotlinModule.Builder().build(),
        ),
) {
    fun run(vararg args: String): Int {
        if (args.isEmpty()) {
            return fail("Usage: show-host-env <node-name> | render-edge-catalog")
        }

        return when (args.first()) {
            "show-host-env" -> showHostEnv(args.drop(1))
            "render-edge-catalog" -> renderEdgeCatalog(args.drop(1))
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

    private fun renderEdgeCatalog(args: List<String>): Int {
        if (args.isNotEmpty()) {
            return fail("Usage: render-edge-catalog")
        }

        val fleet = fleetLoader.load(repositoryRoot.resolve("platform/inventory/fleet.yaml"))
        val catalog = fleet.toEdgeCatalog()
        stdout.append(yamlMapper.writeValueAsString(catalog))
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

private fun PlatformFleet.toEdgeCatalog(): EdgeCatalog {
    val exposureByService =
        buildMap {
            exposureIntent.public.forEach { put(it, "public") }
            exposureIntent.publicAndLan.forEach { put(it, "public_and_lan") }
            exposureIntent.internalOnly.forEach { put(it, "internal_only") }
            exposureIntent.lanOnly.forEach { put(it, "lan_only") }
        }

    val services =
        exposureByService.entries
            .sortedBy { it.key }
            .map { (serviceName, exposure) ->
                EdgeServiceCatalogEntry(
                    name = serviceName,
                    exposure = exposure,
                    access =
                        when {
                            serviceName in accessIntent.ssoProtected -> "sso_protected"
                            exposure == "internal_only" -> "cluster_internal"
                            else -> "direct"
                        },
                )
            }

    return EdgeCatalog(
        cluster = cluster.name,
        services = services,
    )
}

private data class EdgeCatalog(
    val cluster: String,
    val services: List<EdgeServiceCatalogEntry>,
)

private data class EdgeServiceCatalogEntry(
    val name: String,
    val exposure: String,
    val access: String,
)

fun main(args: Array<String>) {
    exitProcess(PlatformInventoryCli().run(*args))
}
