package com.jorisjonkers.personalstack.platform.inventory

import com.fasterxml.jackson.annotation.JsonProperty

data class PlatformFleet(
    val version: Int,
    val cluster: ClusterInfo,
    val sites: Map<String, SiteInfo>,
    val nodes: Map<String, NodeInfo>,
    @param:JsonProperty("service_intent")
    val serviceIntent: ServiceIntent,
    @param:JsonProperty("placement_intent")
    val placementIntent: PlacementIntent,
    @param:JsonProperty("exposure_intent")
    val exposureIntent: ExposureIntent,
    @param:JsonProperty("access_intent")
    val accessIntent: AccessIntent = AccessIntent(),
    @param:JsonProperty("ingress_intent")
    val ingressIntent: IngressIntent = IngressIntent(),
    @param:JsonProperty("monitoring_intent")
    val monitoringIntent: MonitoringIntent = MonitoringIntent(),
)

data class ClusterInfo(
    val name: String,
    @param:JsonProperty("public_domain")
    val publicDomain: String,
    val kubernetes: KubernetesClusterInfo,
)

data class KubernetesClusterInfo(
    @param:JsonProperty("bootstrap_control_plane")
    val bootstrapControlPlane: String,
    @param:JsonProperty("api_server_endpoint")
    val apiServerEndpoint: String,
    @param:JsonProperty("control_plane_token_file")
    val controlPlaneTokenFile: String,
    @param:JsonProperty("worker_join_token_file")
    val workerJoinTokenFile: String,
)

data class SiteInfo(
    val kind: String,
    val purpose: String,
    val networking: SiteNetworking? = null,
)

data class SiteNetworking(
    @param:JsonProperty("lan_ingress_ip")
    val lanIngressIp: String? = null,
    @param:JsonProperty("wan_public_ip")
    val wanPublicIp: String? = null,
)

data class NodeInfo(
    val status: String,
    val site: String,
    val arch: String,
    val ssh: SshConnection? = null,
    @param:JsonProperty("bootstrap_ssh")
    val bootstrapSsh: SshConnection? = null,
    @param:JsonProperty("target_roles")
    val targetRoles: List<String>,
    val capacity: NodeCapacity,
    val gpus: List<GpuInfo> = emptyList(),
    val capabilities: List<String>,
)

data class SshConnection(
    val host: String,
    val user: String,
    val port: Int,
)

data class NodeCapacity(
    @param:JsonProperty("cpu_millicores")
    val cpuMillicores: Int,
    @param:JsonProperty("memory_mib")
    val memoryMib: Int,
)

data class GpuInfo(
    val vendor: String,
    val model: String,
    val `class`: String,
)

data class ServiceIntent(
    val kubernetes: KubernetesServiceIntent,
    @param:JsonProperty("host_native")
    val hostNative: Map<String, List<String>>,
)

data class KubernetesServiceIntent(
    @param:JsonProperty("public_apps")
    val publicApps: List<String>,
    @param:JsonProperty("internal_platform")
    val internalPlatform: List<String>,
    @param:JsonProperty("home_media")
    val homeMedia: List<String>,
)

data class PlacementIntent(
    @param:JsonProperty("frankfurt_only")
    val frankfurtOnly: List<String>,
    @param:JsonProperty("enschede_only")
    val enschedeOnly: List<String>,
    @param:JsonProperty("gpu_specific")
    val gpuSpecific: Map<String, GpuPlacementIntent>,
)

data class GpuPlacementIntent(
    @param:JsonProperty("preferred_gpu_model")
    val preferredGpuModel: String,
    @param:JsonProperty("temporary_gpu_model")
    val temporaryGpuModel: String,
)

data class ExposureIntent(
    val public: List<String>,
    @param:JsonProperty("public_and_lan")
    val publicAndLan: List<String>,
    @param:JsonProperty("internal_only")
    val internalOnly: List<String>,
    @param:JsonProperty("lan_only")
    val lanOnly: List<String>,
)

data class AccessIntent(
    @param:JsonProperty("sso_protected")
    val ssoProtected: List<String> = emptyList(),
    @param:JsonProperty("host_labels")
    val hostLabels: Map<String, String> = emptyMap(),
)

data class IngressIntent(
    @param:JsonProperty("kubernetes_backends")
    val kubernetesBackends: Map<String, KubernetesIngressBackend> = emptyMap(),
    @param:JsonProperty("wan_origin_overrides")
    val wanOriginOverrides: Map<String, String> = emptyMap(),
)

data class MonitoringIntent(
    @param:JsonProperty("kubernetes_backends")
    val kubernetesBackends: Map<String, KubernetesIngressBackend> = emptyMap(),
)

data class KubernetesIngressBackend(
    val namespace: String,
    @param:JsonProperty("service")
    val serviceName: String,
    val port: Int,
    val health: HealthEndpoint? = null,
)

data class HealthEndpoint(
    val type: String = "http",
    val path: String = "/",
    val port: Int? = null,
    @param:JsonProperty("expected_status")
    val expectedStatus: Int? = null,
    @param:JsonProperty("probe_strategy")
    val probeStrategy: String? = null,
)
