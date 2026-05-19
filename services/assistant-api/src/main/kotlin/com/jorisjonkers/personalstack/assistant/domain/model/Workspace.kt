package com.jorisjonkers.personalstack.assistant.domain.model

import java.time.Instant

/**
 * A Workspace is a long-lived "place where agents work". Either a
 * GitHub repo clone or a repo-less Q&A environment.
 *
 * `repoUrl` is null for the Q&A flavour — the runner Pod still mounts
 * an empty workspace volume so file operations are possible, just
 * without a clone or push target.
 */
data class Workspace(
    val id: WorkspaceId,
    val name: String,
    val repoUrl: String?,
    val branch: String?,
    val podName: String?,
    val pvcName: String?,
    val gatewayEndpoint: String?,
    val status: WorkspaceStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    /**
     * Optional link to a GithubLink under a Project. When set, the
     * orchestrator stamps the link's per-repo deploy key into a
     * workspace-scoped k8s Secret and mounts that instead of the
     * cluster-wide default. Ad-hoc workspaces (no Project) leave
     * this null and fall back to the shared `agents-github-deploy-key`.
     */
    val githubLinkId: GithubLinkId? = null,
) {
    val isRepoBacked: Boolean get() = repoUrl != null

    fun withPodInfo(
        podName: String,
        pvcName: String,
        gatewayEndpoint: String,
    ): Workspace =
        copy(
            podName = podName,
            pvcName = pvcName,
            gatewayEndpoint = gatewayEndpoint,
            status = WorkspaceStatus.STARTING,
            updatedAt = Instant.now(),
        )

    fun markReady(): Workspace = copy(status = WorkspaceStatus.READY, updatedAt = Instant.now())

    fun markFailed(): Workspace = copy(status = WorkspaceStatus.FAILED, updatedAt = Instant.now())

    fun markDestroyed(): Workspace = copy(status = WorkspaceStatus.DESTROYED, updatedAt = Instant.now())
}
