package com.jorisjonkers.personalstack.platform.inventory

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Path
import kotlin.io.path.inputStream

class PlatformFleetLoader(
    private val objectMapper: ObjectMapper =
        ObjectMapper(YAMLFactory())
            .registerModule(
                KotlinModule.Builder()
                    .build(),
            ).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true),
) {
    fun load(path: Path): PlatformFleet =
        path.inputStream().use { input ->
            objectMapper.readValue(input, PlatformFleet::class.java)
        }.also(::validate)

    private fun validate(fleet: PlatformFleet) {
        val bootstrapControlPlane =
            requireNotNull(fleet.nodes[fleet.cluster.kubernetes.bootstrapControlPlane]) {
                "bootstrap control plane ${fleet.cluster.kubernetes.bootstrapControlPlane} is not defined as a node"
            }
        require("k3s-control-plane" in bootstrapControlPlane.targetRoles) {
            "bootstrap control plane ${fleet.cluster.kubernetes.bootstrapControlPlane} must target the k3s-control-plane role"
        }
        require(fleet.cluster.kubernetes.apiServerEndpoint.startsWith("https://")) {
            "cluster kubernetes api_server_endpoint must use https"
        }
        require(fleet.cluster.kubernetes.controlPlaneTokenFile.startsWith("/")) {
            "cluster kubernetes control_plane_token_file must be an absolute path"
        }
        require(fleet.cluster.kubernetes.workerJoinTokenFile.startsWith("/")) {
            "cluster kubernetes worker_join_token_file must be an absolute path"
        }

        fleet.nodes.forEach { (nodeName, node) ->
            require(node.site in fleet.sites) {
                "node $nodeName references unknown site ${node.site}"
            }
            if (node.status == "active") {
                require(node.ssh != null) {
                    "active node $nodeName must define ssh connection details"
                }
            }
        }

        val knownServices =
            buildSet {
                addAll(fleet.serviceIntent.kubernetes.publicApps)
                addAll(fleet.serviceIntent.kubernetes.internalPlatform)
                addAll(fleet.serviceIntent.kubernetes.homeMedia)
                fleet.serviceIntent.hostNative.values.forEach(::addAll)
            }
        val knownKubernetesServices =
            buildSet {
                addAll(fleet.serviceIntent.kubernetes.publicApps)
                addAll(fleet.serviceIntent.kubernetes.internalPlatform)
                addAll(fleet.serviceIntent.kubernetes.homeMedia)
            }
        val externallyExposedServices =
            buildSet {
                addAll(fleet.exposureIntent.public)
                addAll(fleet.exposureIntent.publicAndLan)
                addAll(fleet.exposureIntent.lanOnly)
            }
        val externallyExposedKubernetesServices = externallyExposedServices.intersect(knownKubernetesServices)

        fleet.accessIntent.ssoProtected.forEach { serviceName ->
            require(serviceName in knownServices) {
                "sso protected service $serviceName is not defined in service intent"
            }
            require(serviceName in externallyExposedServices) {
                "sso protected service $serviceName must be externally exposed"
            }
        }

        fleet.accessIntent.hostLabels.forEach { (serviceName, hostLabel) ->
            require(serviceName in knownServices) {
                "host label for service $serviceName references unknown service intent"
            }
            require(hostLabel.isNotBlank()) {
                "host label for service $serviceName must not be blank"
            }
        }

        externallyExposedServices.forEach { serviceName ->
            require(serviceName in fleet.accessIntent.hostLabels) {
                "externally exposed service $serviceName must declare a host label"
            }
        }

        fleet.ingressIntent.kubernetesBackends.forEach { (serviceName, backend) ->
            require(serviceName in knownKubernetesServices) {
                "ingress backend for service $serviceName references unknown kubernetes service intent"
            }
            require(serviceName in externallyExposedKubernetesServices) {
                "ingress backend for service $serviceName must target an externally exposed kubernetes service"
            }
            require(backend.namespace.isNotBlank()) {
                "ingress backend namespace for service $serviceName must not be blank"
            }
            require(backend.serviceName.isNotBlank()) {
                "ingress backend service name for service $serviceName must not be blank"
            }
            require(backend.port > 0) {
                "ingress backend port for service $serviceName must be positive"
            }
            backend.health?.let { health ->
                require(health.type in setOf("http", "tcp")) {
                    "health type for service $serviceName must be http or tcp"
                }
                if (health.type == "tcp") {
                    require(health.path == "/") {
                        "tcp health for service $serviceName must not set a path"
                    }
                    require(health.expectedStatus == null) {
                        "tcp health for service $serviceName must not set expected_status"
                    }
                }
                require(health.path.startsWith("/")) {
                    "health path for service $serviceName must start with /"
                }
                health.port?.let { port ->
                    require(port > 0) {
                        "health port for service $serviceName must be positive"
                    }
                }
                health.probeStrategy?.let { strategy ->
                    require(strategy in setOf("internal", "external", "both")) {
                        "health probe_strategy for service $serviceName must be internal, external, or both"
                    }
                    if (strategy in setOf("external", "both")) {
                        require(serviceName in fleet.accessIntent.hostLabels) {
                            "health probe_strategy $strategy for service $serviceName requires a host label"
                        }
                    }
                }
            }
        }

        externallyExposedKubernetesServices.forEach { serviceName ->
            require(serviceName in fleet.ingressIntent.kubernetesBackends) {
                "externally exposed kubernetes service $serviceName must declare an ingress backend"
            }
        }

        fleet.monitoringIntent.kubernetesBackends.forEach { (serviceName, backend) ->
            require(serviceName in knownKubernetesServices) {
                "monitoring backend for service $serviceName references unknown kubernetes service intent"
            }
            require(serviceName !in externallyExposedKubernetesServices) {
                "monitoring backend for service $serviceName duplicates an ingress backend; probe it via ingress_intent instead"
            }
            require(backend.namespace.isNotBlank()) {
                "monitoring backend namespace for service $serviceName must not be blank"
            }
            require(backend.serviceName.isNotBlank()) {
                "monitoring backend service name for service $serviceName must not be blank"
            }
            require(backend.port > 0) {
                "monitoring backend port for service $serviceName must be positive"
            }
            backend.health?.let { health ->
                require(health.type in setOf("http", "tcp")) {
                    "monitoring health type for service $serviceName must be http or tcp"
                }
                if (health.type == "tcp") {
                    require(health.path == "/") {
                        "tcp monitoring health for service $serviceName must not set a path"
                    }
                    require(health.expectedStatus == null) {
                        "tcp monitoring health for service $serviceName must not set expected_status"
                    }
                }
                require(health.path.startsWith("/")) {
                    "monitoring health path for service $serviceName must start with /"
                }
                health.port?.let { port ->
                    require(port > 0) {
                        "monitoring health port for service $serviceName must be positive"
                    }
                }
                require(health.probeStrategy == null || health.probeStrategy == "internal") {
                    "monitoring health probe_strategy for service $serviceName must be internal (monitoring targets have no external host)"
                }
            }
        }

        val lanExposedServices =
            buildSet {
                addAll(fleet.exposureIntent.publicAndLan)
                addAll(fleet.exposureIntent.lanOnly)
            }
        if (lanExposedServices.isNotEmpty()) {
            require(fleet.sites.values.any { it.networking?.lanIngressIp != null }) {
                "lan exposed services require at least one site lan ingress ip"
            }
            require(
                fleet.nodes.values.any { node ->
                    node.status == "active" && "lan-ingress" in node.capabilities
                },
            ) {
                "lan exposed services require at least one active lan ingress node"
            }
        }
    }
}
