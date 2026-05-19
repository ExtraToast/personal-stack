package com.jorisjonkers.personalstack.assistant.infrastructure.web

import com.jorisjonkers.personalstack.assistant.application.command.AddGithubLinkCommand
import com.jorisjonkers.personalstack.assistant.application.command.AttachDeployKeyCommand
import com.jorisjonkers.personalstack.assistant.application.command.CreateProjectCommand
import com.jorisjonkers.personalstack.assistant.application.command.RemoveGithubLinkCommand
import com.jorisjonkers.personalstack.assistant.application.query.ProjectQueryService
import com.jorisjonkers.personalstack.assistant.domain.model.GithubLinkId
import com.jorisjonkers.personalstack.assistant.domain.model.ProjectId
import com.jorisjonkers.personalstack.assistant.infrastructure.web.dto.AddGithubLinkRequest
import com.jorisjonkers.personalstack.assistant.infrastructure.web.dto.AttachDeployKeyRequest
import com.jorisjonkers.personalstack.assistant.infrastructure.web.dto.CreateProjectRequest
import com.jorisjonkers.personalstack.assistant.infrastructure.web.dto.GithubLinkResponse
import com.jorisjonkers.personalstack.assistant.infrastructure.web.dto.ProjectResponse
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
@RequestMapping("/api/v1/projects")
class ProjectController(
    private val commandBus: CommandBus,
    private val projectQuery: ProjectQueryService,
) {
    @PostMapping
    fun create(
        @Valid @RequestBody req: CreateProjectRequest,
    ): ResponseEntity<ProjectResponse> {
        val id = ProjectId.random()
        commandBus.dispatch(
            CreateProjectCommand(
                projectId = id,
                name = req.name,
                slug = req.slug,
                description = req.description ?: "",
            ),
        )
        val view = projectQuery.get(id) ?: error("project not visible immediately after create")
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.of(view.project))
    }

    @GetMapping
    fun list(): List<ProjectResponse> = projectQuery.list().map(ProjectResponse::of)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): ResponseEntity<Map<String, Any>> {
        val view = projectQuery.get(ProjectId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "project" to ProjectResponse.of(view.project),
                "links" to view.links.map(GithubLinkResponse::of),
            ),
        )
    }

    @PostMapping("/{id}/links")
    fun addLink(
        @PathVariable id: UUID,
        @Valid @RequestBody req: AddGithubLinkRequest,
    ): ResponseEntity<GithubLinkResponse> {
        val linkId = GithubLinkId.random()
        commandBus.dispatch(
            AddGithubLinkCommand(
                linkId = linkId,
                projectId = ProjectId(id),
                name = req.name,
                repoUrl = req.repoUrl,
                defaultBranch = req.defaultBranch,
            ),
        )
        val link =
            projectQuery
                .get(ProjectId(id))
                ?.links
                ?.firstOrNull { it.id == linkId }
                ?: error("link not visible after add")
        return ResponseEntity.status(HttpStatus.CREATED).body(GithubLinkResponse.of(link))
    }

    @PostMapping("/{projectId}/links/{linkId}/key")
    fun attachKey(
        @PathVariable projectId: UUID,
        @PathVariable linkId: UUID,
        @Valid @RequestBody req: AttachDeployKeyRequest,
    ): ResponseEntity<Void> {
        commandBus.dispatch(
            AttachDeployKeyCommand(
                linkId = GithubLinkId(linkId),
                privateKeyOpenssh = req.privateKeyOpenssh,
                publicKeyOpenssh = req.publicKeyOpenssh,
                knownHosts = req.knownHosts,
            ),
        )
        return ResponseEntity.accepted().build()
    }

    @DeleteMapping("/{projectId}/links/{linkId}")
    fun removeLink(
        @PathVariable projectId: UUID,
        @PathVariable linkId: UUID,
    ): ResponseEntity<Void> {
        commandBus.dispatch(RemoveGithubLinkCommand(GithubLinkId(linkId)))
        return ResponseEntity.noContent().build()
    }
}
