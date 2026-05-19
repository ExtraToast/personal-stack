package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.GithubLinkId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.common.command.Command

/**
 * Two flavours:
 *
 * 1. Project-backed: `githubLinkId` set, `repoUrl` / `branch`
 *    left null. The handler resolves the link, populates the
 *    workspace's repoUrl + branch from it, and the orchestrator
 *    stamps the per-link deploy key into the Pod.
 *
 * 2. Ad-hoc: `githubLinkId` null. `repoUrl` may be null (Q&A
 *    workspace) or any string the operator types. The cluster-
 *    wide deploy key is mounted as before.
 */
data class CreateWorkspaceCommand(
    val workspaceId: WorkspaceId,
    val name: String,
    val repoUrl: String?,
    val branch: String?,
    val githubLinkId: GithubLinkId? = null,
) : Command
