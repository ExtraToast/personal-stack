package com.jorisjonkers.personalstack.assistant.infrastructure.web.dto

import com.jorisjonkers.personalstack.assistant.domain.model.Turn
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentKind
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSession
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
)

data class WorkspaceResponse(
    val id: UUID,
    val name: String,
    val repoUrl: String?,
    val branch: String?,
    val podName: String?,
    val gatewayEndpoint: String?,
    val status: String,
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
                createdAt = w.createdAt,
                updatedAt = w.updatedAt,
            )
    }
}

data class StartAgentSessionRequest(val kind: WorkspaceAgentKind)

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

data class SendUserInputRequest(val text: String, val enter: Boolean = true)

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
