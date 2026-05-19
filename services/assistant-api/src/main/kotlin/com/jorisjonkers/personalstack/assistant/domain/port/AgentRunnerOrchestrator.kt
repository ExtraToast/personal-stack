package com.jorisjonkers.personalstack.assistant.domain.port

import com.jorisjonkers.personalstack.assistant.domain.model.Workspace

/**
 * Driven port: cluster-level Pod lifecycle for a workspace. The
 * fabric8 implementation lives in `infrastructure/k8s/` and is the
 * only place that touches the Kubernetes API. Keeping this port
 * narrow (provision, destroy, lookup) makes the application layer
 * agnostic to the cluster backend — useful both for test doubles
 * and for the planned eventual move to a fly.io / lightweight VM
 * alternative for adversarial workloads.
 */
interface AgentRunnerOrchestrator {
    data class RunnerHandle(
        val podName: String,
        val pvcName: String,
        val gatewayEndpoint: String,
    )

    fun provision(workspace: Workspace): RunnerHandle

    fun destroy(workspace: Workspace)

    fun isReady(workspace: Workspace): Boolean
}
