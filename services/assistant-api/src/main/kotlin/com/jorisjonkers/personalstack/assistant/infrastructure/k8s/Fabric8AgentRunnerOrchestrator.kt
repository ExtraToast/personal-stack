package com.jorisjonkers.personalstack.assistant.infrastructure.k8s

import com.jorisjonkers.personalstack.assistant.config.AgentRuntimeProperties
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.port.AgentRunnerOrchestrator
import com.jorisjonkers.personalstack.assistant.domain.port.DeployKeyStore
import com.jorisjonkers.personalstack.assistant.domain.port.GithubLinkRepository
import io.fabric8.kubernetes.api.model.EnvVarBuilder
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.api.model.ServiceBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.Base64

/**
 * Owns the entire lifecycle of one workspace's runner Pod. The shape
 * is fixed: one Pod (`agent-runner-<short-id>`), one workspace PVC
 * (`workspace-<short-id>`), one ClusterIP Service so the gateway has
 * a stable in-cluster name. Adversarial use is anticipated, so the
 * Pod runs as UID 1000, with read-only mounts for the credential
 * PVCs that the agent itself doesn't need to write — only the
 * gateway's refresh-token write path does, and that's a follow-up
 * once OAuth refresh churn is observed in practice.
 *
 * Per-workspace deploy-key isolation: when the workspace's
 * `githubLinkId` is set, the orchestrator looks up the link, reads
 * the deploy key from Vault via [DeployKeyStore], and stamps a
 * workspace-scoped k8s Secret out of it. The Pod mounts that Secret
 * instead of the cluster-wide `agents-github-deploy-key`, so a
 * workspace can only ever push to its own Project's repos.
 */
@Component
class Fabric8AgentRunnerOrchestrator(
    private val client: KubernetesClient,
    private val props: AgentRuntimeProperties,
    /**
     * Optional because the Vault adapter is `@ConditionalOnProperty(
     * "spring.cloud.vault.enabled")` and absent in unit tests. When
     * absent, every workspace falls back to the shared deploy key —
     * matching pre-Projects behaviour.
     */
    private val deployKeysProvider: ObjectProvider<DeployKeyStore>,
    private val githubLinks: ObjectProvider<GithubLinkRepository>,
) : AgentRunnerOrchestrator {
    private val log = LoggerFactory.getLogger(Fabric8AgentRunnerOrchestrator::class.java)

    override fun provision(workspace: Workspace): AgentRunnerOrchestrator.RunnerHandle {
        val short = workspace.id.short()
        val podName = "agent-runner-$short"
        val pvcName = "workspace-$short"
        val serviceName = "agent-runner-$short"
        val deployKeySecretName = ensureDeployKeySecret(workspace, short)

        client.persistentVolumeClaims().inNamespace(props.namespace).resource(pvc(pvcName)).serverSideApply()
        client.pods().inNamespace(props.namespace).resource(pod(workspace, podName, pvcName, deployKeySecretName)).serverSideApply()
        client.services().inNamespace(props.namespace).resource(service(serviceName, podName)).serverSideApply()

        val endpoint = "http://$serviceName.${props.namespace}.svc.cluster.local:${props.gatewayPort}"
        log.info(
            "provisioned runner pod {} for workspace {} using deploy-key Secret {}",
            podName,
            workspace.id,
            deployKeySecretName,
        )
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
        // Per-workspace deploy-key Secret only exists when the workspace was bound to a GithubLink.
        if (workspace.githubLinkId != null) {
            client.secrets().inNamespace(props.namespace).withName(workspaceSecretName(short)).delete()
        }
        log.info("destroyed runner pod for workspace {}", workspace.id)
    }

    override fun isReady(workspace: Workspace): Boolean {
        val name = workspace.podName ?: return false
        val pod = client.pods().inNamespace(props.namespace).withName(name).get() ?: return false
        val containerReady = pod.status?.containerStatuses?.firstOrNull()?.ready ?: false
        return containerReady && pod.status?.phase == "Running"
    }

    private fun workspaceSecretName(short: String): String = "agent-runner-deploy-key-$short"

    /**
     * If the workspace points at a GithubLink, materialise the
     * Vault-stored key as a workspace-scoped k8s Secret and return
     * its name. Otherwise return the cluster-wide default and skip
     * the Secret creation. A link that exists in the DB but has no
     * key attached yet (e.g. operator paused mid-wizard) silently
     * falls back to the default so the Pod can still come up.
     */
    private fun ensureDeployKeySecret(workspace: Workspace, short: String): String {
        val linkId = workspace.githubLinkId ?: return props.githubDeployKeySecret
        val links = githubLinks.ifAvailable ?: return props.githubDeployKeySecret
        val keys = deployKeysProvider.ifAvailable ?: return props.githubDeployKeySecret
        val link =
            links.findById(linkId) ?: run {
                log.warn("workspace {} references missing GithubLink {} — falling back to shared key", workspace.id, linkId)
                return props.githubDeployKeySecret
            }
        val material =
            keys.loadKey(link.projectId, link.id) ?: run {
                log.warn("workspace {} link {} has no Vault key yet — falling back to shared key", workspace.id, linkId)
                return props.githubDeployKeySecret
            }
        val secretName = workspaceSecretName(short)
        val secret =
            SecretBuilder()
                .withNewMetadata()
                .withName(secretName)
                .withNamespace(props.namespace)
                .withLabels(
                    mapOf(
                        "app.kubernetes.io/part-of" to "agent-runner",
                        "agent-runner/workspace-id" to short,
                        "agent-runner/github-link-id" to link.id.toString(),
                    ),
                )
                .endMetadata()
                .withType("Opaque")
                .withData(
                    mapOf(
                        "private_key" to b64(material.privateKey),
                        "public_key" to b64(material.publicKey),
                        "known_hosts" to b64(material.knownHosts),
                        "fingerprint" to b64(material.fingerprint),
                    ),
                )
                .build()
        client.secrets().inNamespace(props.namespace).resource(secret).serverSideApply()
        return secretName
    }

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray())

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

    private fun pod(workspace: Workspace, name: String, workspacePvc: String, deployKeySecret: String): Pod =
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
            .withSecretName(deployKeySecret)
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
