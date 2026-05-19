package com.jorisjonkers.personalstack.assistant.infrastructure.ws

import com.fasterxml.jackson.databind.ObjectMapper
import com.jorisjonkers.personalstack.assistant.domain.model.Turn
import com.jorisjonkers.personalstack.assistant.domain.model.TurnId
import com.jorisjonkers.personalstack.assistant.domain.model.TurnRole
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionId
import com.jorisjonkers.personalstack.assistant.application.idle.WorkspaceActivityTracker
import com.jorisjonkers.personalstack.assistant.application.rag.LessonAutoCapture
import com.jorisjonkers.personalstack.assistant.domain.port.TurnRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceAgentSessionRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * WebSocket bridge: browser <-> assistant-api <-> agent-gateway.
 *
 * Per browser-attach, this handler:
 *   1. Opens an upstream WS to the runner's gateway
 *      `/ws/agents/{gatewayAgentId}/attach`.
 *   2. Forwards each inbound text frame to the upstream and appends
 *      a USER Turn to the transcript.
 *   3. Forwards each upstream frame to the browser and buffers the
 *      agent output. Buffered bytes flush to a single AGENT Turn
 *      every `flushIntervalMs` (or on disconnect) — turn-per-byte is
 *      pointless and storing one turn per agent reply lines up with
 *      the eventual Block protocol's "block = turn" granularity.
 *
 * The Turn writer runs on a single shared scheduler; per-session
 * buffers live in a ConcurrentHashMap keyed by the assistant-api WS
 * session id.
 */
@Component
class SessionAttachHandler(
    private val sessions: WorkspaceAgentSessionRepository,
    private val workspaces: WorkspaceRepository,
    private val turns: TurnRepository,
    private val mapper: ObjectMapper,
    private val autoCapture: LessonAutoCapture,
    private val activity: WorkspaceActivityTracker,
) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(SessionAttachHandler::class.java)

    private data class Bridge(
        val sessionId: WorkspaceAgentSessionId,
        val upstream: WebSocketSession,
        val buffer: StringBuilder,
        val flushTask: java.util.concurrent.ScheduledFuture<*>,
    )

    private val bridges = ConcurrentHashMap<String, Bridge>()
    private val scheduler: ScheduledExecutorService =
        Executors.newScheduledThreadPool(2) { r ->
            Thread(r, "session-attach-flusher").apply { isDaemon = true }
        }
    private val client = StandardWebSocketClient()

    override fun afterConnectionEstablished(clientSession: WebSocketSession) {
        val sessionId = sessionIdOf(clientSession) ?: run {
            clientSession.close(CloseStatus.BAD_DATA.withReason("missing sessionId"))
            return
        }
        val agentSession = sessions.findById(sessionId) ?: run {
            clientSession.close(CloseStatus.BAD_DATA.withReason("unknown session"))
            return
        }
        val workspace = workspaces.findById(agentSession.workspaceId) ?: run {
            clientSession.close(CloseStatus.SERVER_ERROR.withReason("workspace gone"))
            return
        }
        val gatewayAgentId = agentSession.gatewayAgentId ?: run {
            clientSession.close(CloseStatus.SERVER_ERROR.withReason("session not bound to a gateway agent"))
            return
        }
        val gatewayBase = workspace.gatewayEndpoint ?: run {
            clientSession.close(CloseStatus.SERVER_ERROR.withReason("workspace has no gateway endpoint"))
            return
        }

        val upstreamUri = URI.create(gatewayBase.replaceFirst("http", "ws") + "/ws/agents/$gatewayAgentId/attach")
        val buffer = StringBuilder()
        val upstreamHandler = UpstreamHandler(clientSession, buffer, mapper)
        val upstream = client.execute(upstreamHandler, upstreamUri.toString()).get(5, TimeUnit.SECONDS)
        val flushTask =
            scheduler.scheduleAtFixedRate(
                { flush(sessionId, buffer) },
                2,
                2,
                TimeUnit.SECONDS,
            )
        bridges[clientSession.id] = Bridge(sessionId, upstream, buffer, flushTask)
        activity.touch(workspace.id)
        log.info("attached client {} to session {} via {}", clientSession.id, sessionId, upstreamUri)
    }

    override fun handleTextMessage(clientSession: WebSocketSession, message: TextMessage) {
        val bridge = bridges[clientSession.id] ?: return
        if (bridge.upstream.isOpen) {
            synchronized(bridge.upstream) { bridge.upstream.sendMessage(message) }
        }
        val payload =
            runCatching { mapper.readValue(message.payload, Map::class.java) }.getOrNull() ?: return
        val input = payload["input"] as? String ?: return
        turns.save(
            Turn(
                id = TurnId.random(),
                sessionId = bridge.sessionId,
                role = TurnRole.USER,
                body = input,
                createdAt = Instant.now(),
            ),
        )
        sessions.findById(bridge.sessionId)?.workspaceId?.let { activity.touch(it) }
    }

    override fun afterConnectionClosed(clientSession: WebSocketSession, status: CloseStatus) {
        val bridge = bridges.remove(clientSession.id) ?: return
        bridge.flushTask.cancel(false)
        flush(bridge.sessionId, bridge.buffer)
        runCatching { bridge.upstream.close(status) }
        // End-of-attach is the natural "session paused" boundary —
        // hand the transcript to the auto-capture pipeline so any
        // marker-flagged or question-shaped pair lands in the KB.
        // Async; the bucket caps total ingest volume per session.
        runCatching { autoCapture.capture(bridge.sessionId) }
    }

    private fun flush(sessionId: WorkspaceAgentSessionId, buffer: StringBuilder) {
        val snapshot: String
        synchronized(buffer) {
            if (buffer.isEmpty()) return
            snapshot = buffer.toString()
            buffer.setLength(0)
        }
        turns.save(
            Turn(
                id = TurnId.random(),
                sessionId = sessionId,
                role = TurnRole.AGENT,
                body = snapshot,
                createdAt = Instant.now(),
            ),
        )
    }

    private fun sessionIdOf(session: WebSocketSession): WorkspaceAgentSessionId? {
        val match =
            Regex("/api/v1/ws/sessions/([0-9a-f-]{36})/attach").find(session.uri?.path ?: return null)
                ?: return null
        return runCatching { WorkspaceAgentSessionId(UUID.fromString(match.groupValues[1])) }.getOrNull()
    }

    /**
     * Inbound from gateway: copy to the buffer (for the Turn record)
     * and shovel the frame straight back to the browser.
     */
    private class UpstreamHandler(
        private val client: WebSocketSession,
        private val buffer: StringBuilder,
        private val mapper: ObjectMapper,
    ) : TextWebSocketHandler() {
        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            if (client.isOpen) {
                synchronized(client) { client.sendMessage(message) }
            }
            val payload =
                runCatching { mapper.readValue(message.payload, Map::class.java) }.getOrNull() ?: return
            val output = payload["output"] as? String ?: return
            synchronized(buffer) { buffer.append(output) }
        }
    }
}
