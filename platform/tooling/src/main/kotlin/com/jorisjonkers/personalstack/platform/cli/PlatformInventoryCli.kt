package com.jorisjonkers.personalstack.platform.cli

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.jorisjonkers.personalstack.platform.RepositoryRootLocator
import com.jorisjonkers.personalstack.platform.inventory.KubernetesIngressBackend
import com.jorisjonkers.personalstack.platform.inventory.NodeInfo
import com.jorisjonkers.personalstack.platform.inventory.PlatformFleet
import com.jorisjonkers.personalstack.platform.inventory.PlatformFleetLoader
import com.jorisjonkers.personalstack.platform.inventory.SshConnection
import java.io.Writer
import java.nio.file.Path
import kotlin.system.exitProcess

class PlatformInventoryCli(
    private val repositoryRoot: Path = RepositoryRootLocator().locate(),
    private val fleetLoader: PlatformFleetLoader = PlatformFleetLoader(),
    private val stdout: Writer = System.out.writer(),
    private val stderr: Writer = System.err.writer(),
    private val yamlMapper: ObjectMapper =
        @Suppress("DEPRECATION")
        ObjectMapper(YAMLFactory())
            .registerModule(
                KotlinModule.Builder().build(),
            ).setSerializationInclusion(JsonInclude.Include.NON_NULL),
) {
    fun run(vararg args: String): Int {
        if (args.isEmpty()) {
            return fail(
                "Usage: show-host-env <node-name> | show-install-host-env <node-name> | render-edge-catalog | render-edge-route-catalog | render-edge-configmap | render-edge-route-configmap | render-traefik-ingressroutes | render-traefik-lan-ingressroutes",
            )
        }

        return when (args.first()) {
            "show-host-env" -> showHostEnv(args.drop(1))
            "show-install-host-env" -> showInstallHostEnv(args.drop(1))
            "render-edge-catalog" -> renderEdgeCatalog(args.drop(1))
            "render-edge-route-catalog" -> renderEdgeRouteCatalog(args.drop(1))
            "render-edge-configmap" -> renderEdgeConfigMap(args.drop(1))
            "render-edge-route-configmap" -> renderEdgeRouteConfigMap(args.drop(1))
            "render-traefik-ingressroutes" -> renderTraefikIngressRoutes(args.drop(1))
            "render-traefik-lan-ingressroutes" -> renderTraefikLanIngressRoutes(args.drop(1))
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

        writeHostEnv(nodeName = nodeName, node = node, ssh = node.ssh, fleet = fleet)
        return 0
    }

    private fun showInstallHostEnv(args: List<String>): Int {
        if (args.size != 1) {
            return fail("Usage: show-install-host-env <node-name>")
        }

        val nodeName = args.single()
        val fleet = fleetLoader.load(repositoryRoot.resolve("platform/inventory/fleet.yaml"))
        val node = fleet.nodes[nodeName] ?: return fail("Unknown node: $nodeName")

        val installSsh =
            when (node.status) {
                "active" -> node.bootstrapSsh ?: node.ssh
                else -> node.bootstrapSsh
            }
        writeHostEnv(nodeName = nodeName, node = node, ssh = installSsh, fleet = fleet)
        return 0
    }

    private fun writeHostEnv(
        nodeName: String,
        node: NodeInfo,
        ssh: SshConnection?,
        fleet: PlatformFleet,
    ) {
        stdout.writeLine("NODE_NAME", nodeName)
        stdout.writeLine("NODE_STATUS", node.status)
        stdout.writeLine("NODE_SITE", node.site)
        stdout.writeLine("NODE_ARCH", node.arch)
        stdout.writeLine("NIX_SYSTEM", node.toNixSystem())
        stdout.writeLine("K3S_BOOTSTRAP_CONTROL_PLANE_NODE", fleet.cluster.kubernetes.bootstrapControlPlane)
        stdout.writeLine("K3S_API_SERVER_ENDPOINT", fleet.cluster.kubernetes.apiServerEndpoint)
        stdout.writeLine("K3S_CONTROL_PLANE_TOKEN_FILE", fleet.cluster.kubernetes.controlPlaneTokenFile)
        stdout.writeLine("K3S_WORKER_JOIN_TOKEN_FILE", fleet.cluster.kubernetes.workerJoinTokenFile)
        stdout.writeLine("HAS_SSH", (ssh != null).toString())
        stdout.writeLine("HAS_BOOTSTRAP_SSH", (node.bootstrapSsh != null).toString())
        stdout.writeLine("SSH_HOST", ssh?.host.orEmpty())
        stdout.writeLine("SSH_USER", ssh?.user.orEmpty())
        stdout.writeLine("SSH_PORT", ssh?.port?.toString().orEmpty())
        stdout.writeLine("BOOTSTRAP_SSH_HOST", node.bootstrapSsh?.host.orEmpty())
        stdout.writeLine("BOOTSTRAP_SSH_USER", node.bootstrapSsh?.user.orEmpty())
        stdout.writeLine("BOOTSTRAP_SSH_PORT", node.bootstrapSsh?.port?.toString().orEmpty())
        stdout.writeLine("IS_CONTROL_PLANE", ("k3s-control-plane" in node.targetRoles).toString())
        stdout.writeLine("IS_WORKER", ("k3s-worker" in node.targetRoles).toString())
        stdout.writeLine("IS_UTILITY_HOST", ("utility-host" in node.targetRoles).toString())
        stdout.writeLine("HAS_NVIDIA", ("nvidia" in node.capabilities).toString())
        stdout.flush()
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

    private fun renderTraefikIngressRoutes(args: List<String>): Int {
        if (args.isNotEmpty()) {
            return fail("Usage: render-traefik-ingressroutes")
        }

        val fleet = fleetLoader.load(repositoryRoot.resolve("platform/inventory/fleet.yaml"))
        stdout.append(fleet.toTraefikIngressRoutesYaml())
        stdout.appendLine()
        stdout.flush()
        return 0
    }

    private fun renderTraefikLanIngressRoutes(args: List<String>): Int {
        if (args.isNotEmpty()) {
            return fail("Usage: render-traefik-lan-ingressroutes")
        }

        val fleet = fleetLoader.load(repositoryRoot.resolve("platform/inventory/fleet.yaml"))
        stdout.append(fleet.toTraefikLanIngressRoutesYaml())
        stdout.appendLine()
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
                    host = accessIntent.hostLabels[serviceName]?.toFqdn(cluster.publicDomain),
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
            .filter { it.host != null }
            .associateBy { it.name }
    val specialCasedServices =
        setOf(
            "app-ui",
            "auth-api",
            "auth-ui",
            "assistant-api",
            "assistant-ui",
        )

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

            externalServices.keys
                .asSequence()
                .filterNot { it in specialCasedServices }
                .sorted()
                .forEach { serviceName ->
                    addDefaultRoute(externalServices.getValue(serviceName))
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

private fun PlatformFleet.toTraefikIngressRoutesYaml(): String {
    val publicServices = exposureIntent.public.toSet() + exposureIntent.publicAndLan.toSet()
    return toTraefikIngressRoutesYaml(
        serviceNames = publicServices,
        ingressClassName = "traefik-public",
        ingressDnsTarget = "ingress.${cluster.publicDomain}",
    )
}

private fun PlatformFleet.toTraefikLanIngressRoutesYaml(): String {
    val lanServices = exposureIntent.publicAndLan.toSet() + exposureIntent.lanOnly.toSet()
    return toTraefikIngressRoutesYaml(
        serviceNames = lanServices,
        ingressClassName = "traefik-lan",
        nameSuffix = "-lan",
        accessOverride = "direct",
    )
}

private fun PlatformFleet.toTraefikIngressRoutesYaml(
    serviceNames: Set<String>,
    ingressClassName: String,
    ingressDnsTarget: String? = null,
    nameSuffix: String = "",
    accessOverride: String? = null,
): String {
    val routes =
        toEdgeRouteCatalog().routes
            .filter { it.service in serviceNames }
            .mapNotNull { route ->
                ingressIntent.kubernetesBackends[route.service]?.let { backend ->
                    route.copy(
                        name = "${route.name}${nameSuffix}",
                        access = accessOverride ?: route.access,
                    ).toIngressRouteYaml(
                        backend = backend,
                        ingressClassName = ingressClassName,
                        ingressDnsTarget = ingressDnsTarget,
                    )
                }
            }

    return routes.joinToString(separator = "\n---\n")
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
        host = this.host ?: error("route $name requires a host"),
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
    val host: String? = null,
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

private fun EdgeRouteCatalogEntry.toIngressRouteYaml(
    backend: KubernetesIngressBackend,
    ingressClassName: String,
    ingressDnsTarget: String? = null,
): String =
    buildString {
        appendLine("apiVersion: traefik.io/v1alpha1")
        appendLine("kind: IngressRoute")
        appendLine("metadata:")
        appendLine("  name: ${name}")
        appendLine("  namespace: edge-system")
        appendLine("  annotations:")
        ingressDnsTarget?.let {
            appendLine("    external-dns.alpha.kubernetes.io/target: ${it}")
            appendLine("    external-dns.alpha.kubernetes.io/cloudflare-proxied: 'true'")
        }
        appendLine("    kubernetes.io/ingress.class: ${ingressClassName}")
        appendLine("spec:")
        appendLine("  entryPoints:")
        appendLine("    - websecure")
        appendLine("  routes:")
        appendLine("    - kind: Rule")
        appendLine("      match: ${toYamlSingleQuotedString(toTraefikMatch())}")
        if (access == "sso_protected") {
            appendLine("      middlewares:")
            appendLine("        - name: forward-auth")
            appendLine("          namespace: edge-system")
        }
        appendLine("      services:")
        appendLine("        - name: ${backend.serviceName}")
        appendLine("          namespace: ${backend.namespace}")
        appendLine("          port: ${backend.port}")
        appendLine("  tls: {}")
    }.trimEnd()

private fun EdgeRouteCatalogEntry.toTraefikMatch(): String {
    val hostMatch = "Host(`${host}`)"
    val positivePredicates =
        buildList {
            pathPrefixes?.forEach { add("PathPrefix(`${it}`)") }
            exactPaths?.forEach { add("Path(`${it}`)") }
        }
    val negativePredicates =
        buildList {
            excludedPathPrefixes?.forEach { add("!PathPrefix(`${it}`)") }
            excludedPaths?.forEach { add("!Path(`${it}`)") }
        }

    val routePredicates =
        buildList {
            add(hostMatch)
            positivePredicates.toCombinedPredicate()?.let(::add)
            addAll(negativePredicates)
        }

    return routePredicates.joinToString(" && ")
}

private fun List<String>.toCombinedPredicate(): String? =
    when (size) {
        0 -> null
        1 -> single()
        else -> joinToString(" || ", prefix = "(", postfix = ")")
    }

private fun toYamlSingleQuotedString(value: String): String =
    "'" + value.replace("'", "''") + "'"

private data class EdgeRouteCatalog(
    val cluster: String,
    val routes: List<EdgeRouteCatalogEntry>,
)

private data class EdgeRouteCatalogEntry(
    val name: String,
    val service: String,
    val host: String,
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
