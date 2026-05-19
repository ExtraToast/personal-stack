package com.jorisjonkers.personalstack.assistant.application.rag

import com.jorisjonkers.personalstack.assistant.config.RagProperties
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionId
import com.jorisjonkers.personalstack.assistant.domain.port.KnowledgeWritePort
import com.jorisjonkers.personalstack.assistant.domain.port.TurnRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceAgentSessionRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Orchestrates the auto-capture pipeline:
 *
 *   sessions ─► turns ─► LessonExtractor ─► TokenBucket gate
 *                                      ─► KnowledgeWritePort.ingestNote
 *
 * `capture(sessionId)` is the public entrypoint and is `@Async` so
 * the WS handler's hot path stays out of the LLM/MCP round trip.
 * The token bucket holds capacity = 3 with a 15-minute refill, so a
 * single session can't flood the KB even if every turn is
 * marker-flagged.
 */
@Component
class LessonAutoCapture(
    private val workspaces: WorkspaceRepository,
    private val sessions: WorkspaceAgentSessionRepository,
    private val turns: TurnRepository,
    private val extractor: LessonExtractor,
    private val knowledgeWrite: KnowledgeWritePort,
    private val rag: RagProperties,
) {
    private val log = LoggerFactory.getLogger(LessonAutoCapture::class.java)
    private val bucket = TokenBucket(capacity = 3, refillInterval = Duration.ofMinutes(15))

    @Async
    open fun capture(sessionId: WorkspaceAgentSessionId) {
        if (!rag.enabled) return
        val session = sessions.findById(sessionId) ?: return
        val workspace = workspaces.findById(session.workspaceId) ?: return
        val candidates = extractor.extract(workspace, turns.findBySessionId(sessionId, limit = 50))
        if (candidates.isEmpty()) return

        val key = sessionId.toString()
        val scope = ScopeInference.scopeFor(workspace)
        for (c in candidates) {
            if (!bucket.tryAcquire(key)) {
                log.info("auto-capture bucket exhausted for session {}", key)
                break
            }
            runCatching { knowledgeWrite.ingestNote(c.title, c.body, scope, c.tags) }
                .onFailure { log.warn("auto-capture ingest failed: {}", it.message) }
        }
    }
}
