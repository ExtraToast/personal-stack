package com.jorisjonkers.personalstack.assistant.infrastructure.k8s

import com.jorisjonkers.personalstack.assistant.config.AgentRuntimeProperties
import com.jorisjonkers.personalstack.assistant.domain.model.GithubLink
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.port.AgentRunnerOrchestrator
import com.jorisjonkers.personalstack.assistant.domain.port.DeployKeyStore
import com.jorisjonkers.personalstack.assistant.domain.port.GithubLinkRepository
import io.fabric8.kubernetes.api.model.ContainerPortBuilder
import io.fabric8.kubernetes.api.model.EnvVarBuilder
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.api.model.ServiceBuilder
import io.fabric8.kubernetes.api.model.VolumeMountBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.Base64

/**
 * Owns the entire lifecycle of one workspace's runner Pod. The shape
 * is fixed: one Pod (`agent-runner-<short-id>`), one workspace PVC
 * (`workspace-<short-id>`), one ClusterIP Service so the gateway has
 * a stable in-cluster name. Adversarial use is anticipated, so the
 * Pod runs as UID 1000, with read-only mounts for the credential
 * PVCs that the agent itself doesn't need to write.
 *
 * Per-workspace deploy-key isolation: when the workspace's
 * `githubLinkId` is set, the orchestrator looks up the link, reads
 * the deploy key from Vault via [DeployKeyStore], and stamps a
 * workspace-scoped k8s Secret out of it. The Pod mounts that Secret
 * instead of the cluster-wide `agents-github-deploy-key`, so a
 * workspace can only ever push to its own Project's repos.
 *
 * fabric8's fluent builder chains naturally split into one helper per
 * pod section (labels, env, mounts, volumes, container body); below
 * the 15-function / LargeClass detekt thresholds would defeat the
 * readability win. The class stays as a single orchestrator because
 * every helper here operates on the same shared props + client.
 */
@Suppress("TooManyFunctions", "LargeClass", "DEPRECATION")
@Component
@Profile("!system-test")
class Fabric8AgentRunnerOrchestrator(
    private val client: KubernetesClient,
    private val props: AgentRuntimeProperties,
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
        applyResources(workspace, podName, pvcName, serviceName, deployKeySecretName)
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

    private fun applyResources(
        workspace: Workspace,
        podName: String,
        pvcName: String,
        serviceName: String,
        deployKeySecretName: String,
    ) {
        client
            .persistentVolumeClaims()
            .inNamespace(props.namespace)
            .resource(pvc(pvcName))
            .serverSideApply()
        client
            .pods()
            .inNamespace(props.namespace)
            .resource(pod(workspace, podName, pvcName, deployKeySecretName))
            .serverSideApply()
        client
            .services()
            .inNamespace(props.namespace)
            .resource(service(serviceName, podName))
            .serverSideApply()
    }

    override fun destroy(workspace: Workspace) {
        val short = workspace.id.short()
        client
            .pods()
            .inNamespace(props.namespace)
            .withName("agent-runner-$short")
            .delete()
        client
            .services()
            .inNamespace(props.namespace)
            .withName("agent-runner-$short")
            .delete()
        client
            .persistentVolumeClaims()
            .inNamespace(props.namespace)
            .withName("workspace-$short")
            .delete()
        // Per-workspace deploy-key Secret only exists when the workspace was bound to a GithubLink.
        if (workspace.githubLinkId != null) {
            client
                .secrets()
                .inNamespace(props.namespace)
                .withName(workspaceSecretName(short))
                .delete()
        }
        log.info("destroyed runner pod for workspace {}", workspace.id)
    }

    override fun isReady(workspace: Workspace): Boolean {
        val name = workspace.podName ?: return false
        val pod =
            client
                .pods()
                .inNamespace(props.namespace)
                .withName(name)
                .get() ?: return false
        val containerReady =
            pod.status
                ?.containerStatuses
                ?.firstOrNull()
                ?.ready ?: false
        return containerReady && pod.status?.phase == "Running"
    }

    private fun workspaceSecretName(short: String): String = "agent-runner-deploy-key-$short"

    /**
     * Resolve the deploy-key Secret name for a workspace. If the
     * workspace is project-backed and the linked GithubLink has a
     * Vault-stored key, that key gets stamped into a workspace-
     * scoped Secret and its name is returned. Every other path —
     * no link, link without key, Vault adapter disabled — falls
     * back to the shared cluster-wide Secret so the Pod can still
     * come up.
     */
    private fun ensureDeployKeySecret(
        workspace: Workspace,
        short: String,
    ): String {
        val material = resolveKeyMaterial(workspace) ?: return props.githubDeployKeySecret
        val secretName = workspaceSecretName(short)
        val secret = buildWorkspaceSecret(secretName, short, material.link, material.key)
        client
            .secrets()
            .inNamespace(props.namespace)
            .resource(secret)
            .serverSideApply()
        return secretName
    }

    private data class ResolvedKey(
        val link: GithubLink,
        val key: DeployKeyStore.KeyMaterial,
    )

    // Early-out validation chain — explicit guards read better than
    // a chained Result here; suppressing detekt's bounded-return
    // and bounded-function rules with intent.
    @Suppress("ReturnCount")
    private fun resolveKeyMaterial(workspace: Workspace): ResolvedKey? {
        val linkId = workspace.githubLinkId ?: return null
        val links = githubLinks.ifAvailable ?: return null
        val keys = deployKeysProvider.ifAvailable ?: return null
        val link =
            links.findById(linkId).also {
                if (it == null) log.warn("workspace {} references missing GithubLink {}", workspace.id, linkId)
            } ?: return null
        val key =
            keys.loadKey(link.projectId, link.id).also {
                if (it == null) log.warn("workspace {} link {} has no Vault key yet", workspace.id, linkId)
            } ?: return null
        return ResolvedKey(link, key)
    }

    private fun buildWorkspaceSecret(
        name: String,
        short: String,
        link: GithubLink,
        material: DeployKeyStore.KeyMaterial,
    ): Secret {
        // fabric8 7.x's typed-builder `withLabels` / `withData`
        // resolve cleanly only when the `Map` literal lives inline
        // at the call site; an extracted `val labels: Map<String,
        // String>` makes Kotlin's overload-resolution choke on K/V
        // type parameters. Inlining is the pragmatic fix.
        return SecretBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(props.namespace)
            .withLabels<String, String>(
                mapOf(
                    "app.kubernetes.io/part-of" to "agent-runner",
                    "agent-runner/workspace-id" to short,
                    "agent-runner/github-link-id" to link.id.toString(),
                ),
            ).endMetadata()
            .withType("Opaque")
            .withData<String, String>(
                mapOf(
                    "private_key" to b64(material.privateKey),
                    "public_key" to b64(material.publicKey),
                    "known_hosts" to b64(material.knownHosts),
                    "fingerprint" to b64(material.fingerprint),
                ),
            ).build()
    }

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray())

    private fun pvc(name: String): PersistentVolumeClaim =
        PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(props.namespace)
            .endMetadata()
            .withNewSpec()
            .withAccessModes("ReadWriteOnce")
            .withStorageClassName(props.workspaceStorageClass)
            .withNewResources()
            .withRequests<String, Quantity>(mapOf("storage" to Quantity(props.workspaceStorageSize)))
            .endResources()
            .endSpec()
            .build()

    // fabric8 builder chains can't be cleanly split into helpers
    // because the intermediate fluent types are private; LongMethod
    // is the natural shape here and suppressed with intent. The
    // inline `mapOf(...)` calls are likewise intentional: extracting
    // them into typed vals trips Kotlin overload resolution on
    // fabric8 7.x's `withLabels` / `withRequests` / `withLimits`.
    @Suppress("LongMethod")
    private fun pod(
        workspace: Workspace,
        name: String,
        workspacePvc: String,
        deployKeySecret: String,
    ): Pod =
        PodBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(props.namespace)
            .withLabels<String, String>(podLabels(workspace))
            .endMetadata()
            .withNewSpec()
            .withServiceAccountName(props.serviceAccount)
            .withNodeSelector<String, String>(props.nodeSelector)
            .withRestartPolicy("Always")
            .withNewSecurityContext()
            .withRunAsUser(RUN_AS_UID)
            .withRunAsGroup(RUN_AS_GID)
            .withFsGroup(FS_GROUP)
            .endSecurityContext()
            .addNewContainer()
            .withName("agent-runner")
            .withImage(props.image)
            .withImagePullPolicy(props.imagePullPolicy)
            .withPorts(ContainerPortBuilder().withName("gateway").withContainerPort(props.gatewayPort).build())
            .withEnv(podEnv(workspace))
            .withVolumeMounts(podVolumeMounts())
            .withNewReadinessProbe()
            .withNewHttpGet()
            .withPath("/healthz")
            .withNewPort("gateway")
            .endHttpGet()
            .withPeriodSeconds(READINESS_PERIOD_SECONDS)
            .withFailureThreshold(READINESS_FAILURE_THRESHOLD)
            .endReadinessProbe()
            .withNewLivenessProbe()
            .withNewHttpGet()
            .withPath("/healthz")
            .withNewPort("gateway")
            .endHttpGet()
            .withPeriodSeconds(LIVENESS_PERIOD_SECONDS)
            .endLivenessProbe()
            .withNewResources()
            .withRequests<String, Quantity>(mapOf("cpu" to Quantity(CPU_REQUEST), "memory" to Quantity(MEMORY_REQUEST)))
            .withLimits<String, Quantity>(mapOf("cpu" to Quantity(CPU_LIMIT), "memory" to Quantity(MEMORY_LIMIT)))
            .endResources()
            .endContainer()
            .withVolumes(podVolumes(workspacePvc, deployKeySecret))
            .endSpec()
            .build()

    private fun podLabels(workspace: Workspace): Map<String, String> =
        mapOf(
            "app.kubernetes.io/name" to "agent-runner",
            "app.kubernetes.io/part-of" to "agent-runner",
            "agent-runner/workspace-id" to workspace.id.short(),
        )

    private fun podEnv(workspace: Workspace) =
        buildList {
            add(EnvVarBuilder().withName("HOME").withValue("/home/agent").build())
            add(EnvVarBuilder().withName("CODEX_HOME").withValue("/home/agent/.codex").build())
            add(EnvVarBuilder().withName("DEPLOYMENT_ENVIRONMENT").withValue("production").build())
            add(EnvVarBuilder().withName("OTEL_SERVICE_NAME").withValue("agent-gateway").build())
            add(
                EnvVarBuilder()
                    .withName("OTEL_EXPORTER_OTLP_ENDPOINT")
                    .withValue("http://alloy.observability.svc.cluster.local:4318")
                    .build(),
            )
            add(EnvVarBuilder().withName("OTEL_EXPORTER_OTLP_PROTOCOL").withValue("http/protobuf").build())
            // The runner Pod is the sandbox (unprivileged, no host access,
            // only its deploy key). IS_SANDBOX tells Claude Code so that
            // --dangerously-skip-permissions runs without the bypass-mode
            // warning + acceptance prompt.
            add(EnvVarBuilder().withName("IS_SANDBOX").withValue("1").build())
            // KB_URL + KB_BEARER_TOKEN are the exact names the
            // knowledge-system install.sh hooks read; without the
            // bearer every hook short-circuits to a no-op and the
            // knowledge.* MCP tools are unreachable from the runner.
            add(EnvVarBuilder().withName("KB_URL").withValue(props.knowledgeBaseUrl).build())
            add(
                EnvVarBuilder()
                    .withName("KB_BEARER_TOKEN")
                    .withNewValueFrom()
                    .withNewSecretKeyRef()
                    .withName(props.knowledgeBearerSecret)
                    .withKey(props.knowledgeBearerSecretKey)
                    .endSecretKeyRef()
                    .endValueFrom()
                    .build(),
            )
            // REPO_URL/REPO_BRANCH drive the entrypoint's boot-time clone
            // into /workspace. Cloning in the runner removes the race that
            // left repo-backed workspaces empty: the old create-time
            // gateway.clone fired before the runner gateway was up and was
            // swallowed. Only repo-backed workspaces carry a repoUrl.
            workspace.repoUrl?.let { url ->
                add(EnvVarBuilder().withName("REPO_URL").withValue(url).build())
                workspace.branch?.let { add(EnvVarBuilder().withName("REPO_BRANCH").withValue(it).build()) }
            }
        }

    private fun podVolumeMounts() =
        listOf(
            VolumeMountBuilder().withName("workspace").withMountPath("/workspace").build(),
            VolumeMountBuilder().withName("claude-credentials").withMountPath("/home/agent/.claude").build(),
            VolumeMountBuilder().withName("codex-credentials").withMountPath("/home/agent/.codex").build(),
            VolumeMountBuilder()
                .withName("github-deploy-key")
                .withMountPath("/var/run/secrets/agents/github-deploy-key")
                .withReadOnly(true)
                .build(),
        )

    private fun podVolumes(
        workspacePvc: String,
        deployKeySecret: String,
    ) = listOf(
        io.fabric8.kubernetes.api.model
            .VolumeBuilder()
            .withName("workspace")
            .withNewPersistentVolumeClaim()
            .withClaimName(workspacePvc)
            .endPersistentVolumeClaim()
            .build(),
        io.fabric8.kubernetes.api.model
            .VolumeBuilder()
            .withName("claude-credentials")
            .withNewPersistentVolumeClaim()
            .withClaimName(props.claudeCredentialsPvc)
            .endPersistentVolumeClaim()
            .build(),
        io.fabric8.kubernetes.api.model
            .VolumeBuilder()
            .withName("codex-credentials")
            .withNewPersistentVolumeClaim()
            .withClaimName(props.codexCredentialsPvc)
            .endPersistentVolumeClaim()
            .build(),
        io.fabric8.kubernetes.api.model
            .VolumeBuilder()
            .withName("github-deploy-key")
            .withNewSecret()
            .withSecretName(deployKeySecret)
            .endSecret()
            .build(),
    )

    private fun service(
        name: String,
        podName: String,
    ): io.fabric8.kubernetes.api.model.Service =
        ServiceBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(props.namespace)
            .endMetadata()
            .withNewSpec()
            .withSelector<String, String>(mapOf("agent-runner/workspace-id" to podName.substringAfter("agent-runner-")))
            .addNewPort()
            .withName("gateway")
            .withPort(props.gatewayPort)
            .withNewTargetPort("gateway")
            .endPort()
            .endSpec()
            .build()

    companion object {
        // Pod security: non-root, with fsGroup so PVC mounts get the right owner.
        private const val RUN_AS_UID = 1000L
        private const val RUN_AS_GID = 1000L
        private const val FS_GROUP = 1000L

        // Resource sizing. Cold start needs headroom for `git clone`
        // + tmux + the gateway jar; runtime steady state is much
        // lighter. Tuned against typical Claude / Codex session.
        private const val CPU_REQUEST = "250m"
        private const val MEMORY_REQUEST = "768Mi"
        private const val CPU_LIMIT = "2000m"
        private const val MEMORY_LIMIT = "3Gi"

        // Probe cadence. Readiness loops 60×5s = 5 min so even a
        // slow first clone + JIT warmup completes before kubelet
        // restarts the container.
        private const val READINESS_PERIOD_SECONDS = 5
        private const val READINESS_FAILURE_THRESHOLD = 60
        private const val LIVENESS_PERIOD_SECONDS = 10
    }
}
