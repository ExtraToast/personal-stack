package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.common.command.Command

/**
 * Attach an additional repository to a workspace. The repo is cloned
 * alongside the primary (it becomes part of REPO_URLS) the next time the
 * runner Pod boots; running pods are not re-provisioned.
 */
data class AttachWorkspaceRepositoryCommand(
    val workspaceId: WorkspaceId,
    val repositoryId: RepositoryId,
) : Command
