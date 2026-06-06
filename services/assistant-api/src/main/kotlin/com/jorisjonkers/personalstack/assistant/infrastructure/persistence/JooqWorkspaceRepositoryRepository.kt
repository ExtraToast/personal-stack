package com.jorisjonkers.personalstack.assistant.infrastructure.persistence

import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepositoryRepository
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqWorkspaceRepositoryRepository(
    private val dsl: DSLContext,
) : WorkspaceRepositoryRepository {
    override fun attach(
        workspaceId: WorkspaceId,
        repositoryId: RepositoryId,
        isPrimary: Boolean,
    ): WorkspaceRepositoryRepository.Link {
        val now = Instant.now()
        dsl
            .insertInto(JUNCTION)
            .set(WORKSPACE_ID, workspaceId.value)
            .set(REPOSITORY_ID, repositoryId.value)
            .set(IS_PRIMARY, isPrimary)
            .set(ATTACHED_AT, now.atOffset(ZoneOffset.UTC))
            .onConflict(WORKSPACE_ID, REPOSITORY_ID)
            .doNothing()
            .execute()
        return WorkspaceRepositoryRepository.Link(workspaceId, repositoryId, isPrimary, now)
    }

    override fun detach(
        workspaceId: WorkspaceId,
        repositoryId: RepositoryId,
    ) {
        dsl
            .deleteFrom(JUNCTION)
            .where(WORKSPACE_ID.eq(workspaceId.value))
            .and(REPOSITORY_ID.eq(repositoryId.value))
            .execute()
    }

    override fun findAllByWorkspaceId(workspaceId: WorkspaceId): List<WorkspaceRepositoryRepository.Link> =
        dsl
            .selectFrom(JUNCTION)
            .where(WORKSPACE_ID.eq(workspaceId.value))
            .orderBy(ATTACHED_AT.asc())
            .fetch()
            .map { it.toLink() }

    private fun Record.toLink(): WorkspaceRepositoryRepository.Link =
        WorkspaceRepositoryRepository.Link(
            workspaceId = WorkspaceId(this[WORKSPACE_ID]),
            repositoryId = RepositoryId(this[REPOSITORY_ID]),
            isPrimary = this[IS_PRIMARY],
            attachedAt = this[ATTACHED_AT].toInstant(),
        )

    companion object {
        @JvmStatic val JUNCTION = DSL.table("workspace_repositories")

        @JvmStatic val WORKSPACE_ID = DSL.field("workspace_id", UUID::class.java)

        @JvmStatic val REPOSITORY_ID = DSL.field("repository_id", UUID::class.java)

        @JvmStatic val IS_PRIMARY = DSL.field("is_primary", Boolean::class.java)

        @JvmStatic val ATTACHED_AT = DSL.field("attached_at", OffsetDateTime::class.java)
    }
}
