package com.jorisjonkers.personalstack.assistant.application.rag

import com.jorisjonkers.personalstack.assistant.domain.model.Turn
import com.jorisjonkers.personalstack.assistant.domain.model.TurnRole
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import org.springframework.stereotype.Component

/**
 * Heuristic extractor that turns a session's transcript into
 * candidate KB lessons. v1 is intentionally simple:
 *
 * - Pair each USER turn with the immediately-following AGENT
 *   turn(s) up to the next USER turn.
 * - Keep the pair only if the agent reply is substantive
 *   (>= minBodyChars) AND either contains an explicit lesson
 *   marker ("TIL:", "Note:", "Lesson:") OR the user turn is a
 *   question (ends with "?" or starts with "how/why/what/...").
 * - The title is the user's prompt, truncated; the body is
 *   "Q: <user>\nA: <agent>" so the captured note has enough
 *   context to be useful in a future recall.
 *
 * The actual quality bar is enforced at the curator step downstream
 * (it routes low-confidence notes into `_inbox/_needs-review`), so
 * this side stays cheap and lets the curator pick.
 */
@Component
class LessonExtractor(
    private val minBodyChars: Int = 240,
    private val maxBodyChars: Int = 4_000,
) {
    private val markerRegex = Regex("\\b(TIL|Note|Lesson|Key takeaway):", RegexOption.IGNORE_CASE)
    private val questionStarter =
        Regex("^\\s*(how|why|what|when|where|which|who|can|does|do|should)\\b", RegexOption.IGNORE_CASE)

    data class Candidate(
        val title: String,
        val body: String,
        val tags: List<String>,
        val confidence: Double,
    )

    fun extract(workspace: Workspace, turns: List<Turn>): List<Candidate> {
        val ordered = turns.sortedBy { it.createdAt }
        val result = mutableListOf<Candidate>()
        var i = 0
        while (i < ordered.size) {
            val turn = ordered[i]
            if (turn.role != TurnRole.USER) {
                i += 1
                continue
            }
            val (agentChunk, advance) = collectAgentReplies(ordered, i + 1)
            i = advance
            if (agentChunk.isBlank() || agentChunk.length < minBodyChars) continue
            if (!isWorthCapturing(turn.body, agentChunk)) continue

            val tags = buildList {
                add("source:agents-ui")
                add("kind:turn-pair")
                if (markerRegex.containsMatchIn(agentChunk)) add("has-marker")
                workspace.repoUrl?.let { add("repo:${ScopeInference.repoSlug(it)}") }
            }

            result.add(
                Candidate(
                    title = makeTitle(workspace, turn.body),
                    body = makeBody(turn.body, agentChunk),
                    tags = tags,
                    confidence = scoreFor(turn.body, agentChunk),
                ),
            )
        }
        return result
    }

    private fun collectAgentReplies(turns: List<Turn>, from: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var j = from
        while (j < turns.size && turns[j].role != TurnRole.USER) {
            if (turns[j].role == TurnRole.AGENT) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(turns[j].body)
            }
            j += 1
        }
        return sb.toString() to j
    }

    private fun isWorthCapturing(userBody: String, agentBody: String): Boolean {
        val isQuestion = userBody.trim().endsWith("?") || questionStarter.containsMatchIn(userBody)
        val hasMarker = markerRegex.containsMatchIn(agentBody)
        return isQuestion || hasMarker
    }

    private fun makeTitle(workspace: Workspace, userBody: String): String {
        val prefix = workspace.name.take(40)
        val tail = userBody.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty().take(120)
        return if (tail.isBlank()) prefix else "$prefix — $tail"
    }

    private fun makeBody(userBody: String, agentBody: String): String {
        val q = userBody.trim().take(maxBodyChars / 4)
        val a = agentBody.trim().take(maxBodyChars * 3 / 4)
        return "Q: $q\n\nA: $a"
    }

    private fun scoreFor(userBody: String, agentBody: String): Double {
        val baseline = 0.35
        val markerBonus = if (markerRegex.containsMatchIn(agentBody)) 0.15 else 0.0
        val lengthBonus = (agentBody.length.coerceAtMost(2_000) / 4_000.0)
        val codeFenceBonus = if (agentBody.contains("```")) 0.05 else 0.0
        return (baseline + markerBonus + lengthBonus + codeFenceBonus).coerceIn(0.0, 0.85)
    }
}
