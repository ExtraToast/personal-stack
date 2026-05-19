package com.jorisjonkers.personalstack.assistant.application.query

import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceStatus
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import org.springframework.stereotype.Service

@Service
class ListWorkspacesQueryService(private val repo: WorkspaceRepository) {
    fun listActive(): List<Workspace> = repo.findAllByStatusNot(WorkspaceStatus.DESTROYED)
}
