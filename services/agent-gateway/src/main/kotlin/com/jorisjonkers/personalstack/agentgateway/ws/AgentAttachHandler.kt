package com.jorisjonkers.personalstack.agentgateway.ws

import com.fasterxml.jackson.databind.ObjectMapper
import com.jorisjonkers.personalstack.agentgateway.tmux.AgentSessionManager
import com.jorisjonkers.personalstack.agentgateway.tmux.LogTailer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * One WS per client-attach. Inbound JSON is `{"input": "...", "enter":
 * true}`; outbound JSON is `{"output": "...bytes-as-utf8..."}`. The
 * envelope intentionally stays trivial — the rich Block protocol
 * (Step 7) lives one layer up, inside assistant-api, which parses the
 * agent's stdout for fenced JSON blocks and emits Block frames to the
 * browser. Keeping the gateway dumb means a CLI flag flip in Claude
 * Code doesn't ripple into the runner image.
 */
@Component
class AgentAttachHandler(
    private val sessions: AgentSessionManager,
    private val mapper: ObjectMapper,
) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(AgentAttachHandler::class.java)
    private val tailers = ConcurrentHashMap<String, LogTailer>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val agentId =
            agentIdOf(session) ?: run {
                session.close(CloseStatus.BAD_DATA.withReason("missing agentId"))
                return
            }
        val agent =
            sessions.get(agentId) ?: run {
                session.close(CloseStatus.BAD_DATA.withReason("unknown agent"))
                return
            }
        log.info("ws attach to agent {} (tmux={})", agentId, agent.tmuxSession)
        val tailer =
            LogTailer(agent.logFile) { bytes ->
                if (session.isOpen) {
                    val msg = mapper.writeValueAsString(mapOf("output" to String(bytes)))
                    synchronized(session) { session.sendMessage(TextMessage(msg)) }
                }
            }
        tailers[session.id] = tailer
        tailer.start()
    }

    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        val agentId = agentIdOf(session) ?: return
        val payload =
            runCatching { mapper.readValue(message.payload, Map::class.java) }
                .getOrElse {
                    log.warn("bad ws payload: {}", message.payload.take(120))
                    return
                }
        val input = payload["input"] as? String ?: return
        val enter = payload["enter"] as? Boolean ?: true
        sessions.send(agentId, input, enter)
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        tailers.remove(session.id)?.close()
    }

    private fun agentIdOf(session: WebSocketSession): String? {
        val path = session.uri?.path ?: return null
        val match = Regex("/ws/agents/([^/]+)/attach").find(path) ?: return null
        return match.groupValues[1]
    }
}
