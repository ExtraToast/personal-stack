package com.jorisjonkers.personalstack.assistant.infrastructure.web.dto

import com.jorisjonkers.personalstack.assistant.domain.model.Turn
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentKind
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSession
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceKind
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateWorkspaceRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 80)
    val name: String,
    val repoUrl: String? = null,
    val branch: String? = null,
    /**
     * Workspace flavour. Defaults to REPO_BACKED so the existing UI
     * (which doesn't yet send this field) keeps the previous shape.
     */
    val kind: WorkspaceKind = WorkspaceKind.REPO_BACKED,
    /**
     * Optional project context. Set when the workspace was opened
     * from a project's UI; null for ad-hoc work.
     */
    val projectId: UUID? = null,
    /**
     * Preferred way to bind a workspace to a repo + its deploy key.
     * When set, `repoUrl` / `branch` are derived from the
     * Repository row.
     */
    val repositoryId: UUID? = null,
    /**
     * Deprecated alias for [repositoryId]. Kept until PR F migrates
     * the assistant-ui to the new field. The server prefers
     * [repositoryId] when both are supplied.
     */
    @Deprecated("Use repositoryId", ReplaceWith("repositoryId"))
    val githubLinkId: UUID? = null,
)

@Suppress("DEPRECATION")
data class WorkspaceResponse(
    val id: UUID,
    val name: String,
    val repoUrl: String?,
    val branch: String?,
    val podName: String?,
    val gatewayEndpoint: String?,
    val status: String,
    val kind: String,
    val projectId: UUID?,
    val repositoryId: UUID?,
    val githubLinkId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun of(w: Workspace) =
            WorkspaceResponse(
                id = w.id.value,
                name = w.name,
                repoUrl = w.repoUrl,
                branch = w.branch,
                podName = w.podName,
                gatewayEndpoint = w.gatewayEndpoint,
                status = w.status.name,
                kind = w.kind.name,
                projectId = w.projectId?.value,
                repositoryId = w.repositoryId?.value,
                githubLinkId = w.githubLinkId?.value,
                createdAt = w.createdAt,
                updatedAt = w.updatedAt,
            )
    }
}

data class StartAgentSessionRequest(
    val kind: WorkspaceAgentKind,
)

data class WorkspaceAgentSessionResponse(
    val id: UUID,
    val workspaceId: UUID,
    val kind: WorkspaceAgentKind,
    val gatewayAgentId: String?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun of(s: WorkspaceAgentSession) =
            WorkspaceAgentSessionResponse(
                id = s.id.value,
                workspaceId = s.workspaceId.value,
                kind = s.kind,
                gatewayAgentId = s.gatewayAgentId,
                status = s.status.name,
                createdAt = s.createdAt,
                updatedAt = s.updatedAt,
            )
    }
}

data class SendUserInputRequest(
    val text: String,
    val enter: Boolean = true,
)

data class StageInputRequest(
    val content: String,
    val name: String? = null,
)

data class StagedInputResponse(
    val path: String,
    val bytes: Long,
    val name: String,
)

data class TurnResponse(
    val id: UUID,
    val sessionId: UUID,
    val role: String,
    val body: String,
    val createdAt: Instant,
) {
    companion object {
        fun of(t: Turn) =
            TurnResponse(
                id = t.id.value,
                sessionId = t.sessionId.value,
                role = t.role.name,
                body = t.body,
                createdAt = t.createdAt,
            )
    }
}
