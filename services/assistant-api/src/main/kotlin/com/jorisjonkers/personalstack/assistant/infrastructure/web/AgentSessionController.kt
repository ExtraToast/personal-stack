package com.jorisjonkers.personalstack.assistant.infrastructure.web

import com.jorisjonkers.personalstack.assistant.application.command.SendUserInputCommand
import com.jorisjonkers.personalstack.assistant.application.command.StartAgentSessionCommand
import com.jorisjonkers.personalstack.assistant.application.command.StopAgentSessionCommand
import com.jorisjonkers.personalstack.assistant.application.query.GetTurnHistoryQueryService
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.infrastructure.web.dto.SendUserInputRequest
import com.jorisjonkers.personalstack.assistant.infrastructure.web.dto.StartAgentSessionRequest
import com.jorisjonkers.personalstack.assistant.infrastructure.web.dto.TurnResponse
import com.jorisjonkers.personalstack.common.command.CommandBus
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/sessions")
class AgentSessionController(
    private val commandBus: CommandBus,
    private val turnHistory: GetTurnHistoryQueryService,
) {
    @PostMapping
    fun start(
        @PathVariable workspaceId: UUID,
        @RequestBody req: StartAgentSessionRequest,
    ): ResponseEntity<Map<String, UUID>> {
        val sessionId = WorkspaceAgentSessionId.random()
        commandBus.dispatch(
            StartAgentSessionCommand(
                sessionId = sessionId,
                workspaceId = WorkspaceId(workspaceId),
                kind = req.kind,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("sessionId" to sessionId.value))
    }

    @PostMapping("/{sessionId}/input")
    fun send(
        @PathVariable workspaceId: UUID,
        @PathVariable sessionId: UUID,
        @RequestBody req: SendUserInputRequest,
    ): ResponseEntity<Void> {
        commandBus.dispatch(
            SendUserInputCommand(
                sessionId = WorkspaceAgentSessionId(sessionId),
                text = req.text,
                enter = req.enter,
            ),
        )
        return ResponseEntity.accepted().build()
    }

    @GetMapping("/{sessionId}/turns")
    fun turns(
        @PathVariable workspaceId: UUID,
        @PathVariable sessionId: UUID,
    ): List<TurnResponse> =
        turnHistory.history(WorkspaceAgentSessionId(sessionId)).map(TurnResponse::of)

    @DeleteMapping("/{sessionId}")
    fun stop(
        @PathVariable workspaceId: UUID,
        @PathVariable sessionId: UUID,
    ): ResponseEntity<Void> {
        commandBus.dispatch(StopAgentSessionCommand(WorkspaceAgentSessionId(sessionId)))
        return ResponseEntity.noContent().build()
    }
}
