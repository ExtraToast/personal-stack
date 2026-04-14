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
        }

        externallyExposedKubernetesServices.forEach { serviceName ->
            require(serviceName in fleet.ingressIntent.kubernetesBackends) {
                "externally exposed kubernetes service $serviceName must declare an ingress backend"
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
