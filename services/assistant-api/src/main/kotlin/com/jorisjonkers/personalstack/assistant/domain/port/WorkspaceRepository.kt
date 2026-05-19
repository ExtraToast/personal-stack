package com.jorisjonkers.personalstack.assistant.domain.port

import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceStatus

interface WorkspaceRepository {
    fun save(workspace: Workspace): Workspace

    fun findById(id: WorkspaceId): Workspace?

    fun findAllByStatusNot(status: WorkspaceStatus): List<Workspace>

    fun delete(id: WorkspaceId)
}
