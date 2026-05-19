package com.jorisjonkers.personalstack.assistant.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.jorisjonkers.personalstack.assistant.application.query.GetWorkspaceQueryService
import com.jorisjonkers.personalstack.assistant.application.query.ListWorkspacesQueryService
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceKind
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceStatus
import com.jorisjonkers.personalstack.common.command.CommandBus
import com.jorisjonkers.personalstack.common.web.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@Suppress("DEPRECATION")
class WorkspaceControllerTest {
    private val commandBus = mockk<CommandBus>(relaxed = true)
    private val listQuery = mockk<ListWorkspacesQueryService>()
    private val getQuery = mockk<GetWorkspaceQueryService>()
    private val objectMapper = ObjectMapper()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = WorkspaceController(commandBus, listQuery, getQuery)
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    private fun workspace(
        id: WorkspaceId = WorkspaceId.random(),
        kind: WorkspaceKind = WorkspaceKind.REPO_BACKED,
        repositoryId: RepositoryId? = null,
    ): Workspace {
        val now = Instant.now()
        return Workspace(
            id = id,
            name = "demo",
            repoUrl = "git@github.com:owner/repo.git",
            branch = "main",
            podName = "pod",
            pvcName = "pvc",
            gatewayEndpoint = "http://endpoint:8090",
            status = WorkspaceStatus.READY,
            createdAt = now,
            updatedAt = now,
            kind = kind,
            repositoryId = repositoryId,
        )
    }

    @Test
    fun `POST creates a workspace and returns 201 with the new shape`() {
        val w = workspace(kind = WorkspaceKind.REPO_BACKED, repositoryId = RepositoryId.random())
        every {
            getQuery.get(any())
        } returns GetWorkspaceQueryService.WorkspaceView(w, emptyList())

        mockMvc
            .perform(
                post("/api/v1/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "name" to "demo",
                                "kind" to "REPO_BACKED",
                                "repositoryId" to w.repositoryId?.value.toString(),
                            ),
                        ),
                    ),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.kind").value("REPO_BACKED"))
            .andExpect(jsonPath("$.status").value("READY"))

        verify { commandBus.dispatch(any()) }
    }

    @Test
    fun `POST returns error on blank name`() {
        mockMvc
            .perform(
                post("/api/v1/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("name" to ""))),
            ).andExpect { result ->
                // standaloneSetup doesn't wire the same validation
                // converter the full app uses; either 4xx is acceptable
                // here — the assertion is "the validation kicked in".
                require(result.response.status in 400..499) {
                    "expected client error, got ${result.response.status}"
                }
            }
    }

    @Test
    fun `GET list returns active workspaces`() {
        every { listQuery.listActive() } returns listOf(workspace(), workspace())
        mockMvc
            .perform(get("/api/v1/workspaces"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `GET by id returns workspace + sessions envelope`() {
        val w = workspace()
        every { getQuery.get(w.id) } returns GetWorkspaceQueryService.WorkspaceView(w, emptyList())
        mockMvc
            .perform(get("/api/v1/workspaces/${w.id.value}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.workspace.id").value(w.id.value.toString()))
            .andExpect(jsonPath("$.sessions").isArray)
    }

    @Test
    fun `GET by id with non-existent returns 404`() {
        every { getQuery.get(any()) } returns null
        mockMvc
            .perform(get("/api/v1/workspaces/${UUID.randomUUID()}"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE destroys workspace and returns 204`() {
        mockMvc
            .perform(delete("/api/v1/workspaces/${UUID.randomUUID()}"))
            .andExpect(status().isNoContent)
        verify { commandBus.dispatch(any()) }
    }
}
