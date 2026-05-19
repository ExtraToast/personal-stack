package com.jorisjonkers.personalstack.assistant.infrastructure.k8s

import com.jorisjonkers.personalstack.assistant.config.AgentRuntimeProperties
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.port.AgentRunnerOrchestrator
import io.fabric8.kubernetes.api.model.EnvVarBuilder
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.ServiceBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Owns the entire lifecycle of one workspace's runner Pod. The shape
 * is fixed: one Pod (`agent-runner-<short-id>`), one workspace PVC
 * (`workspace-<short-id>`), one ClusterIP Service so the gateway has
 * a stable in-cluster name. Adversarial use is anticipated, so the
 * Pod runs as UID 1000, with read-only mounts for the credential
 * PVCs that the agent itself doesn't need to write — only the
 * gateway's refresh-token write path does, and that's a follow-up
 * once OAuth refresh churn is observed in practice.
 */
@Component
class Fabric8AgentRunnerOrchestrator(
    private val client: KubernetesClient,
    private val props: AgentRuntimeProperties,
) : AgentRunnerOrchestrator {
    private val log = LoggerFactory.getLogger(Fabric8AgentRunnerOrchestrator::class.java)

    override fun provision(workspace: Workspace): AgentRunnerOrchestrator.RunnerHandle {
        val short = workspace.id.short()
        val podName = "agent-runner-$short"
        val pvcName = "workspace-$short"
        val serviceName = "agent-runner-$short"

        client.persistentVolumeClaims().inNamespace(props.namespace).resource(pvc(pvcName)).serverSideApply()
        client.pods().inNamespace(props.namespace).resource(pod(workspace, podName, pvcName)).serverSideApply()
        client.services().inNamespace(props.namespace).resource(service(serviceName, podName)).serverSideApply()

        val endpoint = "http://$serviceName.${props.namespace}.svc.cluster.local:${props.gatewayPort}"
        log.info("provisioned runner pod {} for workspace {}", podName, workspace.id)
        return AgentRunnerOrchestrator.RunnerHandle(
            podName = podName,
            pvcName = pvcName,
            gatewayEndpoint = endpoint,
        )
    }

    override fun destroy(workspace: Workspace) {
        val short = workspace.id.short()
        client.pods().inNamespace(props.namespace).withName("agent-runner-$short").delete()
        client.services().inNamespace(props.namespace).withName("agent-runner-$short").delete()
        client.persistentVolumeClaims().inNamespace(props.namespace).withName("workspace-$short").delete()
        log.info("destroyed runner pod for workspace {}", workspace.id)
    }

    override fun isReady(workspace: Workspace): Boolean {
        val name = workspace.podName ?: return false
        val pod = client.pods().inNamespace(props.namespace).withName(name).get() ?: return false
        val containerReady = pod.status?.containerStatuses?.firstOrNull()?.ready ?: false
        return containerReady && pod.status?.phase == "Running"
    }

    private fun pvc(name: String): PersistentVolumeClaim =
        PersistentVolumeClaimBuilder()
            .withNewMetadata().withName(name).withNamespace(props.namespace).endMetadata()
            .withNewSpec()
            .withAccessModes("ReadWriteOnce")
            .withStorageClassName(props.workspaceStorageClass)
            .withNewResources()
            .withRequests(mapOf("storage" to Quantity(props.workspaceStorageSize)))
            .endResources()
            .endSpec()
            .build()

    private fun pod(workspace: Workspace, name: String, workspacePvc: String): Pod =
        PodBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(props.namespace)
            .withLabels(
                mapOf(
                    "app.kubernetes.io/name" to "agent-runner",
                    "app.kubernetes.io/part-of" to "agent-runner",
                    "agent-runner/workspace-id" to workspace.id.short(),
                ),
            )
            .endMetadata()
            .withNewSpec()
            .withServiceAccountName(props.serviceAccount)
            .withNodeSelector(props.nodeSelector)
            .withRestartPolicy("Always")
            .withNewSecurityContext()
            .withRunAsUser(1000L)
            .withRunAsGroup(1000L)
            .withFsGroup(1000L)
            .endSecurityContext()
            .addNewContainer()
            .withName("agent-runner")
            .withImage(props.image)
            .withImagePullPolicy(props.imagePullPolicy)
            .withPorts(
                io.fabric8.kubernetes.api.model.ContainerPortBuilder()
                    .withName("gateway")
                    .withContainerPort(props.gatewayPort)
                    .build(),
            )
            .withEnv(
                EnvVarBuilder().withName("HOME").withValue("/home/agent").build(),
                EnvVarBuilder().withName("CODEX_HOME").withValue("/home/agent/.codex").build(),
                EnvVarBuilder().withName("DEPLOYMENT_ENVIRONMENT").withValue("production").build(),
                EnvVarBuilder().withName("OTEL_SERVICE_NAME").withValue("agent-gateway").build(),
                EnvVarBuilder()
                    .withName("OTEL_EXPORTER_OTLP_ENDPOINT")
                    .withValue("http://alloy.observability.svc.cluster.local:4318")
                    .build(),
                EnvVarBuilder().withName("OTEL_EXPORTER_OTLP_PROTOCOL").withValue("http/protobuf").build(),
            )
            .withVolumeMounts(
                io.fabric8.kubernetes.api.model.VolumeMountBuilder()
                    .withName("workspace").withMountPath("/workspace").build(),
                io.fabric8.kubernetes.api.model.VolumeMountBuilder()
                    .withName("claude-credentials").withMountPath("/home/agent/.claude").build(),
                io.fabric8.kubernetes.api.model.VolumeMountBuilder()
                    .withName("codex-credentials").withMountPath("/home/agent/.codex").build(),
                io.fabric8.kubernetes.api.model.VolumeMountBuilder()
                    .withName("github-deploy-key")
                    .withMountPath("/var/run/secrets/agents/github-deploy-key")
                    .withReadOnly(true)
                    .build(),
            )
            .withNewReadinessProbe()
            .withNewHttpGet()
            .withPath("/healthz")
            .withNewPort("gateway")
            .endHttpGet()
            .withPeriodSeconds(5)
            .withFailureThreshold(60)
            .endReadinessProbe()
            .withNewLivenessProbe()
            .withNewHttpGet()
            .withPath("/healthz")
            .withNewPort("gateway")
            .endHttpGet()
            .withPeriodSeconds(10)
            .endLivenessProbe()
            .withNewResources()
            .withRequests(
                mapOf(
                    "cpu" to Quantity("250m"),
                    "memory" to Quantity("768Mi"),
                ),
            )
            .withLimits(
                mapOf(
                    "cpu" to Quantity("2000m"),
                    "memory" to Quantity("3Gi"),
                ),
            )
            .endResources()
            .endContainer()
            .addNewVolume()
            .withName("workspace")
            .withNewPersistentVolumeClaim()
            .withClaimName(workspacePvc)
            .endPersistentVolumeClaim()
            .endVolume()
            .addNewVolume()
            .withName("claude-credentials")
            .withNewPersistentVolumeClaim()
            .withClaimName(props.claudeCredentialsPvc)
            .endPersistentVolumeClaim()
            .endVolume()
            .addNewVolume()
            .withName("codex-credentials")
            .withNewPersistentVolumeClaim()
            .withClaimName(props.codexCredentialsPvc)
            .endPersistentVolumeClaim()
            .endVolume()
            .addNewVolume()
            .withName("github-deploy-key")
            .withNewSecret()
            .withSecretName(props.githubDeployKeySecret)
            .endSecret()
            .endVolume()
            .endSpec()
            .build()

    private fun service(name: String, podName: String): io.fabric8.kubernetes.api.model.Service =
        ServiceBuilder()
            .withNewMetadata().withName(name).withNamespace(props.namespace).endMetadata()
            .withNewSpec()
            .withSelector(mapOf("agent-runner/workspace-id" to podName.substringAfter("agent-runner-")))
            .addNewPort().withName("gateway").withPort(props.gatewayPort).withNewTargetPort("gateway").endPort()
            .endSpec()
            .build()
}
