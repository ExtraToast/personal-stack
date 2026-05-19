package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.GithubLinkId
import com.jorisjonkers.personalstack.assistant.domain.model.ProjectId
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceKind
import com.jorisjonkers.personalstack.common.command.Command

/**
 * Three flavours dictated by [kind]:
 *
 * 1. `REPO_BACKED`: `repositoryId` set (preferred) or the legacy
 *    `githubLinkId` set during the V9 migration window. The
 *    handler resolves the repository, populates `repoUrl` /
 *    `branch` from it, and the orchestrator stamps the per-repo
 *    deploy key into the Pod.
 *
 * 2. `SCRATCH`: `repositoryId` / `githubLinkId` left null. The
 *    runner Pod boots without a clone; `repoUrl` may still be
 *    supplied if the operator wants a free-text URL for future
 *    use, but the orchestrator does not act on it.
 *
 * 3. `CHAT`: not handled here — chat lives in [ChatSession] and
 *    is created via [StartChatSessionCommand]. A `kind = CHAT`
 *    workspace creation request is rejected.
 *
 * [projectId] is optional context: setting it groups the workspace
 * under a project in the UI without otherwise affecting the Pod.
 */
data class CreateWorkspaceCommand(
    val workspaceId: WorkspaceId,
    val name: String,
    val repoUrl: String?,
    val branch: String?,
    val kind: WorkspaceKind = WorkspaceKind.REPO_BACKED,
    val projectId: ProjectId? = null,
    val repositoryId: RepositoryId? = null,
    @Deprecated(
        "Use repositoryId — kept for the V9 migration window so existing UI " +
            "callers continue to work until PR F migrates them.",
        ReplaceWith("repositoryId"),
    )
    val githubLinkId: GithubLinkId? = null,
) : Command
