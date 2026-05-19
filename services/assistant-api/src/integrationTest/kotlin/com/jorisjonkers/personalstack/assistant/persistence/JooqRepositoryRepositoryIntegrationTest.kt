package com.jorisjonkers.personalstack.assistant.persistence

import com.jorisjonkers.personalstack.assistant.IntegrationTestBase
import com.jorisjonkers.personalstack.assistant.domain.model.Project
import com.jorisjonkers.personalstack.assistant.domain.model.ProjectId
import com.jorisjonkers.personalstack.assistant.domain.model.Repository
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.port.ProjectRepositoryRepository
import com.jorisjonkers.personalstack.assistant.domain.port.ProjectsRepository
import com.jorisjonkers.personalstack.assistant.domain.port.RepositoryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

class JooqRepositoryRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var repositories: RepositoryRepository

    @Autowired
    private lateinit var junction: ProjectRepositoryRepository

    @Autowired
    private lateinit var projects: ProjectsRepository

    private fun newRepository(name: String = "repo-${UUID.randomUUID()}") =
        Repository(
            id = RepositoryId.random(),
            name = name,
            repoUrl = "git@github.com:owner/$name.git",
            defaultBranch = "main",
            vaultKeyPath = "secret/data/agents/repositories/x",
            deployKeyFingerprint = null,
            deployKeyAddedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `save and findById round-trip`() {
        val r = newRepository()
        repositories.save(r)

        val loaded = repositories.findById(r.id)
        assertThat(loaded).isNotNull
        assertThat(loaded!!.name).isEqualTo(r.name)
        assertThat(loaded.repoUrl).isEqualTo(r.repoUrl)
    }

    @Test
    fun `findByName returns the repository by globally-unique name`() {
        val r = newRepository(name = "lookup-by-name-${UUID.randomUUID()}")
        repositories.save(r)

        val loaded = repositories.findByName(r.name)
        assertThat(loaded).isNotNull
        assertThat(loaded!!.id).isEqualTo(r.id)
    }

    @Test
    fun `save updates fingerprint on conflict`() {
        val r = newRepository()
        repositories.save(r)
        val withKey =
            r.copy(
                deployKeyFingerprint = "SHA256:abc",
                deployKeyAddedAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        repositories.save(withKey)

        val loaded = repositories.findById(r.id)
        assertThat(loaded!!.deployKeyFingerprint).isEqualTo("SHA256:abc")
        assertThat(loaded.deployKeyAddedAt).isNotNull
    }

    @Test
    fun `findAllByProjectId returns only linked repositories`() {
        val project =
            Project(
                id = ProjectId.random(),
                name = "test-project",
                slug = "test-project-${UUID.randomUUID().toString().take(6)}",
                description = "",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        projects.save(project)
        val r1 = newRepository()
        val r2 = newRepository()
        val r3 = newRepository()
        repositories.save(r1)
        repositories.save(r2)
        repositories.save(r3)
        junction.link(project.id, r1.id)
        junction.link(project.id, r2.id)

        val attached = repositories.findAllByProjectId(project.id)

        assertThat(attached.map { it.id }).containsExactlyInAnyOrder(r1.id, r2.id)
    }

    @Test
    fun `delete removes the row`() {
        val r = newRepository()
        repositories.save(r)
        repositories.delete(r.id)
        assertThat(repositories.findById(r.id)).isNull()
    }
}
