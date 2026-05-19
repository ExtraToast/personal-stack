package com.jorisjonkers.personalstack.assistant.infrastructure.persistence

import com.jorisjonkers.personalstack.assistant.domain.model.GithubLinkId
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceStatus
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Uses jOOQ's untyped DSL rather than the codegen classes. Codegen
 * would work but the generated classes only appear after a successful
 * build with the new Flyway migrations applied; the untyped form
 * makes the boot path resilient to a fresh checkout that hasn't run
 * the codegen task yet. Same pattern as auth-api's
 * audit-log persistence, which predates the codegen plugin.
 */
@Repository
class JooqWorkspaceRepository(private val dsl: DSLContext) : WorkspaceRepository {
    override fun save(workspace: Workspace): Workspace {
        val createdAt = workspace.createdAt.atOffset(ZoneOffset.UTC)
        val updatedAt = workspace.updatedAt.atOffset(ZoneOffset.UTC)
        dsl
            .insertInto(WORKSPACES)
            .set(ID, workspace.id.value)
            .set(NAME, workspace.name)
            .set(REPO_URL, workspace.repoUrl)
            .set(BRANCH, workspace.branch)
            .set(POD_NAME, workspace.podName)
            .set(PVC_NAME, workspace.pvcName)
            .set(GATEWAY_ENDPOINT, workspace.gatewayEndpoint)
            .set(STATUS, workspace.status.name)
            .set(GITHUB_LINK_ID, workspace.githubLinkId?.value)
            .set(CREATED_AT, createdAt)
            .set(UPDATED_AT, updatedAt)
            .onConflict(ID)
            .doUpdate()
            .set(NAME, workspace.name)
            .set(REPO_URL, workspace.repoUrl)
            .set(BRANCH, workspace.branch)
            .set(POD_NAME, workspace.podName)
            .set(PVC_NAME, workspace.pvcName)
            .set(GATEWAY_ENDPOINT, workspace.gatewayEndpoint)
            .set(STATUS, workspace.status.name)
            .set(GITHUB_LINK_ID, workspace.githubLinkId?.value)
            .set(UPDATED_AT, updatedAt)
            .execute()
        return workspace
    }

    override fun findById(id: WorkspaceId): Workspace? =
        dsl.selectFrom(WORKSPACES).where(ID.eq(id.value)).fetchOne()?.toWorkspace()

    override fun findAllByStatusNot(status: WorkspaceStatus): List<Workspace> =
        dsl
            .selectFrom(WORKSPACES)
            .where(STATUS.ne(status.name))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toWorkspace() }

    override fun delete(id: WorkspaceId) {
        dsl.deleteFrom(WORKSPACES).where(ID.eq(id.value)).execute()
    }

    private fun Record.toWorkspace(): Workspace =
        Workspace(
            id = WorkspaceId(this[ID] as UUID),
            name = this[NAME] as String,
            repoUrl = this[REPO_URL] as String?,
            branch = this[BRANCH] as String?,
            podName = this[POD_NAME] as String?,
            pvcName = this[PVC_NAME] as String?,
            gatewayEndpoint = this[GATEWAY_ENDPOINT] as String?,
            status = WorkspaceStatus.valueOf(this[STATUS] as String),
            githubLinkId = (this[GITHUB_LINK_ID] as UUID?)?.let { GithubLinkId(it) },
            createdAt = (this[CREATED_AT] as OffsetDateTime).toInstant(),
            updatedAt = (this[UPDATED_AT] as OffsetDateTime).toInstant(),
        )

    companion object {
        @JvmStatic val WORKSPACES = DSL.table("workspaces")

        @JvmStatic val ID = DSL.field("id", UUID::class.java)

        @JvmStatic val NAME = DSL.field("name", String::class.java)

        @JvmStatic val REPO_URL = DSL.field("repo_url", String::class.java)

        @JvmStatic val BRANCH = DSL.field("branch", String::class.java)

        @JvmStatic val POD_NAME = DSL.field("pod_name", String::class.java)

        @JvmStatic val PVC_NAME = DSL.field("pvc_name", String::class.java)

        @JvmStatic val GATEWAY_ENDPOINT = DSL.field("gateway_endpoint", String::class.java)

        @JvmStatic val STATUS = DSL.field("status", String::class.java)

        @JvmStatic val GITHUB_LINK_ID = DSL.field("github_link_id", UUID::class.java)

        @JvmStatic val CREATED_AT = DSL.field("created_at", OffsetDateTime::class.java)

        @JvmStatic val UPDATED_AT = DSL.field("updated_at", OffsetDateTime::class.java)

        @Suppress("unused")
        private val unusedLdt = LocalDateTime::class.java
    }
}
