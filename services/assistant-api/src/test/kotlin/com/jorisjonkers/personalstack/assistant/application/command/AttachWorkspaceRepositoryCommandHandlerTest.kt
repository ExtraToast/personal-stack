package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.Repository
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.port.RepositoryRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepositoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AttachWorkspaceRepositoryCommandHandlerTest {
    private val workspaces = mockk<WorkspaceRepository>()
    private val repositories = mockk<RepositoryRepository>()
    private val links = mockk<WorkspaceRepositoryRepository>(relaxed = true)
    private val handler = AttachWorkspaceRepositoryCommandHandler(workspaces, repositories, links)

    private val workspaceId = WorkspaceId.random()
    private val repositoryId = RepositoryId.random()
    private val command = AttachWorkspaceRepositoryCommand(workspaceId, repositoryId)

    @Test
    fun `attaches a non-primary link when both exist`() {
        every { workspaces.findById(workspaceId) } returns mockk<Workspace>()
        every { repositories.findById(repositoryId) } returns mockk<Repository>()

        handler.handle(command)

        verify { links.attach(workspaceId, repositoryId, isPrimary = false) }
    }

    @Test
    fun `rejects an unknown workspace`() {
        every { workspaces.findById(workspaceId) } returns null

        assertThrows<NoSuchElementException> { handler.handle(command) }
        verify(exactly = 0) { links.attach(any(), any(), any()) }
    }

    @Test
    fun `rejects an unknown repository`() {
        every { workspaces.findById(workspaceId) } returns mockk<Workspace>()
        every { repositories.findById(repositoryId) } returns null

        assertThrows<NoSuchElementException> { handler.handle(command) }
        verify(exactly = 0) { links.attach(any(), any(), any()) }
    }
}
