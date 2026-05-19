package com.jorisjonkers.personalstack.assistant.infrastructure.persistence

import com.jorisjonkers.personalstack.assistant.domain.model.ProjectId
import com.jorisjonkers.personalstack.assistant.domain.model.Repository
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.port.RepositoryRepository
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository as SpringRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@SpringRepository
class JooqRepositoryRepository(
    private val dsl: DSLContext,
) : RepositoryRepository {
    override fun save(repository: Repository): Repository {
        val createdAt = repository.createdAt.atOffset(ZoneOffset.UTC)
        val updatedAt = repository.updatedAt.atOffset(ZoneOffset.UTC)
        val addedAt = repository.deployKeyAddedAt?.atOffset(ZoneOffset.UTC)
        dsl
            .insertInto(REPOSITORIES)
            .set(ID, repository.id.value)
            .set(NAME, repository.name)
            .set(REPO_URL, repository.repoUrl)
            .set(DEFAULT_BRANCH, repository.defaultBranch)
            .set(VAULT_KEY_PATH, repository.vaultKeyPath)
            .set(FINGERPRINT, repository.deployKeyFingerprint)
            .set(KEY_ADDED_AT, addedAt)
            .set(CREATED_AT, createdAt)
            .set(UPDATED_AT, updatedAt)
            .onConflict(ID)
            .doUpdate()
            .set(NAME, repository.name)
            .set(REPO_URL, repository.repoUrl)
            .set(DEFAULT_BRANCH, repository.defaultBranch)
            .set(VAULT_KEY_PATH, repository.vaultKeyPath)
            .set(FINGERPRINT, repository.deployKeyFingerprint)
            .set(KEY_ADDED_AT, addedAt)
            .set(UPDATED_AT, updatedAt)
            .execute()
        return repository
    }

    override fun findById(id: RepositoryId): Repository? =
        dsl
            .selectFrom(REPOSITORIES)
            .where(ID.eq(id.value))
            .fetchOne()
            ?.toRepository()

    override fun findByName(name: String): Repository? =
        dsl
            .selectFrom(REPOSITORIES)
            .where(NAME.eq(name))
            .fetchOne()
            ?.toRepository()

    override fun findAll(): List<Repository> =
        dsl
            .selectFrom(REPOSITORIES)
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toRepository() }

    override fun findAllByProjectId(projectId: ProjectId): List<Repository> =
        dsl
            .select(
                ID,
                NAME,
                REPO_URL,
                DEFAULT_BRANCH,
                VAULT_KEY_PATH,
                FINGERPRINT,
                KEY_ADDED_AT,
                CREATED_AT,
                UPDATED_AT,
            ).from(REPOSITORIES)
            .innerJoin(JUNCTION)
            .on(JUNCTION_REPOSITORY_ID.eq(ID))
            .where(JUNCTION_PROJECT_ID.eq(projectId.value))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toRepository() }

    override fun delete(id: RepositoryId) {
        dsl.deleteFrom(REPOSITORIES).where(ID.eq(id.value)).execute()
    }

    private fun Record.toRepository(): Repository =
        Repository(
            id = RepositoryId(this[ID]),
            name = this[NAME],
            repoUrl = this[REPO_URL],
            defaultBranch = this[DEFAULT_BRANCH],
            vaultKeyPath = this[VAULT_KEY_PATH],
            deployKeyFingerprint = this[FINGERPRINT],
            deployKeyAddedAt = this[KEY_ADDED_AT]?.toInstant(),
            createdAt = this[CREATED_AT].toInstant(),
            updatedAt = this[UPDATED_AT].toInstant(),
        )

    companion object {
        @JvmStatic val REPOSITORIES = DSL.table("repositories")

        @JvmStatic val ID = DSL.field("id", UUID::class.java)

        @JvmStatic val NAME = DSL.field("name", String::class.java)

        @JvmStatic val REPO_URL = DSL.field("repo_url", String::class.java)

        @JvmStatic val DEFAULT_BRANCH = DSL.field("default_branch", String::class.java)

        @JvmStatic val VAULT_KEY_PATH = DSL.field("vault_key_path", String::class.java)

        @JvmStatic val FINGERPRINT = DSL.field("deploy_key_fingerprint", String::class.java)

        @JvmStatic val KEY_ADDED_AT = DSL.field("deploy_key_added_at", OffsetDateTime::class.java)

        @JvmStatic val CREATED_AT = DSL.field("created_at", OffsetDateTime::class.java)

        @JvmStatic val UPDATED_AT = DSL.field("updated_at", OffsetDateTime::class.java)

        @JvmStatic val JUNCTION = DSL.table("project_repositories")

        @JvmStatic val JUNCTION_PROJECT_ID = DSL.field("project_repositories.project_id", UUID::class.java)

        @JvmStatic val JUNCTION_REPOSITORY_ID = DSL.field("project_repositories.repository_id", UUID::class.java)
    }
}
