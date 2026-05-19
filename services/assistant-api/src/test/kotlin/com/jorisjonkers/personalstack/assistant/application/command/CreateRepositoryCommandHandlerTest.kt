package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.Repository
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.port.RepositoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class CreateRepositoryCommandHandlerTest {
    private val repositories = mockk<RepositoryRepository>()
    private val handler = CreateRepositoryCommandHandler(repositories)

    @Test
    fun `handle creates a repository with per-repo vault path and no fingerprint yet`() {
        every { repositories.findByName(any()) } returns null
        val saved = slot<Repository>()
        every { repositories.save(capture(saved)) } answers { saved.captured }

        val id = RepositoryId.random()
        handler.handle(
            CreateRepositoryCommand(
                repositoryId = id,
                name = "  personal-stack  ",
                repoUrl = "  git@github.com:owner/repo.git  ",
                defaultBranch = "",
            ),
        )

        assertThat(saved.captured.id).isEqualTo(id)
        assertThat(saved.captured.name).isEqualTo("personal-stack")
        assertThat(saved.captured.repoUrl).isEqualTo("git@github.com:owner/repo.git")
        assertThat(saved.captured.defaultBranch).isEqualTo("main")
        assertThat(saved.captured.vaultKeyPath).isEqualTo("secret/data/agents/repositories/$id")
        assertThat(saved.captured.deployKeyFingerprint).isNull()
        assertThat(saved.captured.deployKeyAddedAt).isNull()
    }

    @Test
    fun `handle rejects blank name`() {
        every { repositories.findByName(any()) } returns null
        assertThrows<IllegalArgumentException> {
            handler.handle(
                CreateRepositoryCommand(RepositoryId.random(), "  ", "git@github.com:o/r.git"),
            )
        }
    }

    @Test
    fun `handle rejects blank repoUrl`() {
        every { repositories.findByName(any()) } returns null
        assertThrows<IllegalArgumentException> {
            handler.handle(
                CreateRepositoryCommand(RepositoryId.random(), "x", " "),
            )
        }
    }

    @Test
    fun `handle rejects name already in use by another repository`() {
        val existing =
            Repository(
                id = RepositoryId.random(),
                name = "personal-stack",
                repoUrl = "git@github.com:other/repo.git",
                defaultBranch = "main",
                vaultKeyPath = "secret/data/agents/repositories/x",
                deployKeyFingerprint = null,
                deployKeyAddedAt = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        every { repositories.findByName("personal-stack") } returns existing
        assertThrows<IllegalStateException> {
            handler.handle(
                CreateRepositoryCommand(RepositoryId.random(), "personal-stack", "git@github.com:owner/repo.git"),
            )
        }
    }
}
