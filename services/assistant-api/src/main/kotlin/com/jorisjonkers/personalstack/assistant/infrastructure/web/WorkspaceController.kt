package com.jorisjonkers.personalstack.assistant.infrastructure.web

import com.jorisjonkers.personalstack.assistant.application.command.AttachWorkspaceRepositoryCommand
import com.jorisjonkers.personalstack.assistant.application.command.CreateWorkspaceCommand
import com.jorisjonkers.personalstack.assistant.application.command.DestroyWorkspaceCommand
import com.jorisjonkers.personalstack.assistant.application.command.DetachWorkspaceRepositoryCommand
import com.jorisjonkers.personalstack.assistant.application.query.GetWorkspaceQueryService
import com.jorisjonkers.personalstack.assistant.application.query.ListWorkspacesQueryService
import com.jorisjonkers.personalstack.assistant.domain.model.GithubLinkId
import com.jorisjonkers.personalstack.assistant.domain.model.ProjectId
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.infrastructure.web.dto.AttachWorkspaceRepositoryRequest
import com.jorisjonkers.personalstack.assistant.infrastructure.web.dto.CreateWorkspaceRequest
import com.jorisjonkers.personalstack.assistant.infrastructure.web.dto.WorkspaceDetailResponse
import com.jorisjonkers.personalstack.assistant.infrastructure.web.dto.WorkspaceResponse
import com.jorisjonkers.personalstack.common.command.CommandBus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/workspaces")
class WorkspaceController(
    private val commandBus: CommandBus,
    private val listQuery: ListWorkspacesQueryService,
    private val getQuery: GetWorkspaceQueryService,
) {
    @Suppress("DEPRECATION")
    @PostMapping
    fun create(
        @Valid @RequestBody req: CreateWorkspaceRequest,
    ): ResponseEntity<WorkspaceResponse> {
        val id = WorkspaceId.random()
        commandBus.dispatch(
            CreateWorkspaceCommand(
                workspaceId = id,
                name = req.name,
                repoUrl = req.repoUrl,
                branch = req.branch,
                kind = req.kind,
                projectId = req.projectId?.let { ProjectId(it) },
                repositoryId = req.repositoryId?.let { RepositoryId(it) },
                githubLinkId = req.githubLinkId?.let { GithubLinkId(it) },
            ),
        )
        val view =
            getQuery.getSummary(id)
                ?: throw IllegalStateException(
                    "Workspace $id was created but not yet visible to the read model; retry the GET in a moment",
                )
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkspaceResponse.of(view))
    }

    @GetMapping
    fun list(): List<WorkspaceResponse> = listQuery.listActive().map(WorkspaceResponse::of)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): ResponseEntity<WorkspaceDetailResponse> {
        val view = getQuery.get(WorkspaceId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(WorkspaceDetailResponse.of(view.workspace, view.repositories, view.sessions))
    }

    @PostMapping("/{id}/repositories")
    fun attachRepository(
        @PathVariable id: UUID,
        @Valid @RequestBody req: AttachWorkspaceRepositoryRequest,
    ): ResponseEntity<Void> {
        commandBus.dispatch(
            AttachWorkspaceRepositoryCommand(
                workspaceId = WorkspaceId(id),
                repositoryId = RepositoryId(requireNotNull(req.repositoryId)),
            ),
        )
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/repositories/{repoId}")
    fun detachRepository(
        @PathVariable id: UUID,
        @PathVariable repoId: UUID,
    ): ResponseEntity<Void> {
        commandBus.dispatch(
            DetachWorkspaceRepositoryCommand(
                workspaceId = WorkspaceId(id),
                repositoryId = RepositoryId(repoId),
            ),
        )
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}")
    fun destroy(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        commandBus.dispatch(DestroyWorkspaceCommand(WorkspaceId(id)))
        return ResponseEntity.noContent().build()
    }
}
