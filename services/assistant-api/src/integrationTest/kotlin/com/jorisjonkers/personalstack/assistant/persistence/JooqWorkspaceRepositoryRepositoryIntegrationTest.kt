package com.jorisjonkers.personalstack.assistant.persistence

import com.jorisjonkers.personalstack.assistant.IntegrationTestBase
import com.jorisjonkers.personalstack.assistant.domain.model.Repository
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceStatus
import com.jorisjonkers.personalstack.assistant.domain.port.RepositoryRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepositoryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

class JooqWorkspaceRepositoryRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var junction: WorkspaceRepositoryRepository

    @Autowired
    private lateinit var workspaces: WorkspaceRepository

    @Autowired
    private lateinit var repositories: RepositoryRepository

    private fun workspace() =
        Workspace(
            id = WorkspaceId.random(),
            name = "w",
            repoUrl = "git@github.com:o/primary.git",
            branch = "main",
            podName = null,
            pvcName = null,
            gatewayEndpoint = null,
            status = WorkspaceStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        ).also(workspaces::save)

    private fun repository() =
        Repository(
            id = RepositoryId.random(),
            name = "r-${UUID.randomUUID()}",
            repoUrl = "git@github.com:o/r.git",
            defaultBranch = "main",
            vaultKeyPath = "x",
            deployKeyFingerprint = null,
            deployKeyAddedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        ).also(repositories::save)

    @Test
    fun `attach inserts a row carried by findAllByWorkspaceId`() {
        val w = workspace()
        val r = repository()
        junction.attach(w.id, r.id, isPrimary = true)

        val links = junction.findAllByWorkspaceId(w.id)
        assertThat(links).hasSize(1)
        assertThat(links.single().repositoryId).isEqualTo(r.id)
        assertThat(links.single().isPrimary).isTrue
    }

    @Test
    fun `attach is idempotent on duplicate insert`() {
        val w = workspace()
        val r = repository()
        junction.attach(w.id, r.id)
        junction.attach(w.id, r.id)
        assertThat(junction.findAllByWorkspaceId(w.id)).hasSize(1)
    }

    @Test
    fun `detach removes the row`() {
        val w = workspace()
        val r = repository()
        junction.attach(w.id, r.id)
        junction.detach(w.id, r.id)
        assertThat(junction.findAllByWorkspaceId(w.id)).isEmpty()
    }

    @Test
    fun `findAllByWorkspaceId returns every attached repository`() {
        val w = workspace()
        val r1 = repository()
        val r2 = repository()
        junction.attach(w.id, r1.id, isPrimary = true)
        junction.attach(w.id, r2.id)

        val links = junction.findAllByWorkspaceId(w.id)
        assertThat(links.map { it.repositoryId }).containsExactlyInAnyOrder(r1.id, r2.id)
        assertThat(links.filter { it.isPrimary }.map { it.repositoryId }).containsExactly(r1.id)
    }
}
