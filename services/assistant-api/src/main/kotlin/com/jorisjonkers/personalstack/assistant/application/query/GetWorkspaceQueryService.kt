package com.jorisjonkers.personalstack.assistant.application.query

import com.jorisjonkers.personalstack.assistant.domain.model.Repository
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSession
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.port.RepositoryRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceAgentSessionRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepositoryRepository
import org.springframework.stereotype.Service

@Service
class GetWorkspaceQueryService(
    private val workspaces: WorkspaceRepository,
    private val sessions: WorkspaceAgentSessionRepository,
    private val workspaceRepositories: WorkspaceRepositoryRepository,
    private val repositories: RepositoryRepository,
) {
    data class WorkspaceView(
        val workspace: Workspace,
        val sessions: List<WorkspaceAgentSession>,
        val repositories: List<Repository>,
    )

    fun getSummary(id: WorkspaceId): Workspace? = workspaces.findById(id)

    fun get(id: WorkspaceId): WorkspaceView? {
        val workspace = workspaces.findById(id) ?: return null
        val resolvedRepositories =
            workspaceRepositories
                .findAllByWorkspaceId(id)
                .map { link ->
                    repositories.findById(link.repositoryId)
                        ?: throw IllegalStateException(
                            "Workspace ${id.value} references missing repository ${link.repositoryId.value}",
                        )
                }
        return WorkspaceView(workspace, sessions.findAllByWorkspaceId(id), resolvedRepositories)
    }
}
