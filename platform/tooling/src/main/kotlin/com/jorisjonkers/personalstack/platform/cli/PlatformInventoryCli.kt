package com.jorisjonkers.personalstack.platform.cli

import com.fasterxml.jackson.annotation.JsonInclude
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
        ObjectMapper(YAMLFactory())
            .registerModule(
                KotlinModule.Builder().build(),
            ).setSerializationInclusion(JsonInclude.Include.NON_NULL),
) {
    fun run(vararg args: String): Int {
        if (args.isEmpty()) {
            return fail("Usage: show-host-env <node-name> | render-edge-catalog | render-edge-route-catalog | render-edge-configmap | render-edge-route-configmap")
        }

        return when (args.first()) {
            "show-host-env" -> showHostEnv(args.drop(1))
            "render-edge-catalog" -> renderEdgeCatalog(args.drop(1))
            "render-edge-route-catalog" -> renderEdgeRouteCatalog(args.drop(1))
            "render-edge-configmap" -> renderEdgeConfigMap(args.drop(1))
            "render-edge-route-configmap" -> renderEdgeRouteConfigMap(args.drop(1))
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

    private fun renderEdgeRouteCatalog(args: List<String>): Int {
        if (args.isNotEmpty()) {
            return fail("Usage: render-edge-route-catalog")
        }

        val fleet = fleetLoader.load(repositoryRoot.resolve("platform/inventory/fleet.yaml"))
        val routes = fleet.toEdgeRouteCatalog()
        stdout.append(yamlMapper.writeValueAsString(routes))
        stdout.flush()
        return 0
    }

    private fun renderEdgeConfigMap(args: List<String>): Int {
        if (args.isNotEmpty()) {
            return fail("Usage: render-edge-configmap")
        }

        val fleet = fleetLoader.load(repositoryRoot.resolve("platform/inventory/fleet.yaml"))
        stdout.append(fleet.toEdgeConfigMapYaml(yamlMapper))
        stdout.flush()
        return 0
    }

    private fun renderEdgeRouteConfigMap(args: List<String>): Int {
        if (args.isNotEmpty()) {
            return fail("Usage: render-edge-route-configmap")
        }

        val fleet = fleetLoader.load(repositoryRoot.resolve("platform/inventory/fleet.yaml"))
        stdout.append(fleet.toEdgeRouteConfigMapYaml(yamlMapper))
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
                    productionHost = accessIntent.hostLabels[serviceName]?.toFqdn(cluster.publicDomain),
                    testHost = accessIntent.hostLabels[serviceName]?.toFqdn(cluster.testDomain),
                )
            }

    return EdgeCatalog(
        cluster = cluster.name,
        services = services,
    )
}

private fun PlatformFleet.toEdgeConfigMapYaml(yamlMapper: ObjectMapper): String {
    val catalogYaml = yamlMapper.writeValueAsString(toEdgeCatalog()).trimEnd()
    return catalogYaml.toConfigMapYaml(
        configMapName = "platform-edge-catalog",
        dataKey = "edge-catalog.yaml",
    )
}

private fun PlatformFleet.toEdgeRouteCatalog(): EdgeRouteCatalog {
    val externalServices =
        toEdgeCatalog().services
            .filter { it.productionHost != null && it.testHost != null }
            .associateBy { it.name }

    val routes =
        buildList {
            externalServices["app-ui"]?.let(::addDefaultRoute)

            externalServices["auth-api"]?.let { authApi ->
                add(
                    authApi.toRoute(
                        name = "auth-api",
                        pathPrefixes = listOf("/api/"),
                    ),
                )
                add(
                    authApi.toRoute(
                        name = "auth-api-well-known",
                        pathPrefixes = listOf("/.well-known/"),
                    ),
                )
            }
            externalServices["auth-ui"]?.let { authUi ->
                add(
                    authUi.toRoute(
                        excludedPathPrefixes = listOf("/api/", "/.well-known/"),
                    ),
                )
            }

            externalServices["assistant-api"]?.let { assistantApi ->
                add(
                    assistantApi.toRoute(
                        pathPrefixes = listOf("/api/"),
                        excludedPaths = listOf("/api/actuator/health", "/api/v1/health"),
                        excludedPathPrefixes = listOf("/api/actuator/health/"),
                    ),
                )
                add(
                    assistantApi.toRoute(
                        name = "assistant-api-health",
                        access = "direct",
                        exactPaths = listOf("/api/actuator/health", "/api/v1/health"),
                        pathPrefixes = listOf("/api/actuator/health/"),
                    ),
                )
            }
            externalServices["assistant-ui"]?.let { assistantUi ->
                add(
                    assistantUi.toRoute(
                        excludedPathPrefixes = listOf("/api/"),
                    ),
                )
            }

            listOf(
                "grafana",
                "headscale",
                "jellyfin",
                "n8n",
                "rabbitmq",
                "radarr",
                "sonarr",
                "uptime-kuma",
                "vault",
                "adguard",
                "samba",
            ).forEach { serviceName ->
                externalServices[serviceName]?.let(::addDefaultRoute)
            }
        }.sortedBy { it.name }

    return EdgeRouteCatalog(
        cluster = cluster.name,
        routes = routes,
    )
}

private fun PlatformFleet.toEdgeRouteConfigMapYaml(yamlMapper: ObjectMapper): String {
    val routeCatalogYaml = yamlMapper.writeValueAsString(toEdgeRouteCatalog()).trimEnd()
    return routeCatalogYaml.toConfigMapYaml(
        configMapName = "platform-edge-route-catalog",
        dataKey = "edge-route-catalog.yaml",
    )
}

private fun MutableList<EdgeRouteCatalogEntry>.addDefaultRoute(service: EdgeServiceCatalogEntry) {
    add(service.toRoute())
}

private fun EdgeServiceCatalogEntry.toRoute(
    name: String = this.name,
    access: String = this.access,
    pathPrefixes: List<String>? = null,
    exactPaths: List<String>? = null,
    excludedPathPrefixes: List<String>? = null,
    excludedPaths: List<String>? = null,
): EdgeRouteCatalogEntry =
    EdgeRouteCatalogEntry(
        name = name,
        service = this.name,
        productionHost = this.productionHost ?: error("route $name requires a production host"),
        testHost = this.testHost ?: error("route $name requires a test host"),
        access = access,
        pathPrefixes = pathPrefixes,
        exactPaths = exactPaths,
        excludedPathPrefixes = excludedPathPrefixes,
        excludedPaths = excludedPaths,
    )

private data class EdgeCatalog(
    val cluster: String,
    val services: List<EdgeServiceCatalogEntry>,
)

private data class EdgeServiceCatalogEntry(
    val name: String,
    val exposure: String,
    val access: String,
    @param:com.fasterxml.jackson.annotation.JsonProperty("production_host")
    val productionHost: String? = null,
    @param:com.fasterxml.jackson.annotation.JsonProperty("test_host")
    val testHost: String? = null,
)

private fun String.toFqdn(domain: String): String =
    if (this == "root") {
        domain
    } else {
        "${this}.${domain}"
    }

private fun String.toConfigMapYaml(
    configMapName: String,
    dataKey: String,
): String =
    let { sourceYaml ->
        buildString {
            appendLine("apiVersion: v1")
            appendLine("kind: ConfigMap")
            appendLine("metadata:")
            appendLine("  name: ${configMapName}")
            appendLine("  namespace: edge-system")
            appendLine("data:")
            appendLine("  ${dataKey}: |")
            sourceYaml.lineSequence().forEach { line ->
                append("    ")
                appendLine(line)
            }
        }
    }

private data class EdgeRouteCatalog(
    val cluster: String,
    val routes: List<EdgeRouteCatalogEntry>,
)

private data class EdgeRouteCatalogEntry(
    val name: String,
    val service: String,
    @param:com.fasterxml.jackson.annotation.JsonProperty("production_host")
    val productionHost: String,
    @param:com.fasterxml.jackson.annotation.JsonProperty("test_host")
    val testHost: String,
    val access: String,
    @param:com.fasterxml.jackson.annotation.JsonProperty("path_prefixes")
    val pathPrefixes: List<String>? = null,
    @param:com.fasterxml.jackson.annotation.JsonProperty("exact_paths")
    val exactPaths: List<String>? = null,
    @param:com.fasterxml.jackson.annotation.JsonProperty("excluded_path_prefixes")
    val excludedPathPrefixes: List<String>? = null,
    @param:com.fasterxml.jackson.annotation.JsonProperty("excluded_paths")
    val excludedPaths: List<String>? = null,
)

fun main(args: Array<String>) {
    exitProcess(PlatformInventoryCli().run(*args))
}
