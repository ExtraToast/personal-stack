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
    private val minBodyChars: Int = MIN_BODY_CHARS,
    private val maxBodyChars: Int = MAX_BODY_CHARS,
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

    private data class Pair(
        val user: Turn,
        val agentBody: String,
    )

    fun extract(
        workspace: Workspace,
        turns: List<Turn>,
    ): List<Candidate> {
        val ordered = turns.sortedBy { it.createdAt }
        return collectPairs(ordered)
            .filter { it.agentBody.length >= minBodyChars && isWorthCapturing(it.user.body, it.agentBody) }
            .map { toCandidate(workspace, it) }
    }

    private fun collectPairs(ordered: List<Turn>): List<Pair> {
        val pairs = mutableListOf<Pair>()
        var i = 0
        while (i < ordered.size) {
            val turn = ordered[i]
            if (turn.role == TurnRole.USER) {
                val (agentBody, next) = collectAgentReplies(ordered, i + 1)
                if (agentBody.isNotBlank()) pairs.add(Pair(turn, agentBody))
                i = next
            } else {
                i += 1
            }
        }
        return pairs
    }

    private fun collectAgentReplies(
        turns: List<Turn>,
        from: Int,
    ): kotlin.Pair<String, Int> {
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

    private fun toCandidate(
        workspace: Workspace,
        pair: Pair,
    ): Candidate {
        val tags =
            buildList {
                add("source:agents-ui")
                add("kind:turn-pair")
                if (markerRegex.containsMatchIn(pair.agentBody)) add("has-marker")
                workspace.repoUrl?.let { add("repo:${ScopeInference.repoSlug(it)}") }
            }
        return Candidate(
            title = makeTitle(workspace, pair.user.body),
            body = makeBody(pair.user.body, pair.agentBody),
            tags = tags,
            confidence = scoreFor(pair.agentBody),
        )
    }

    private fun isWorthCapturing(
        userBody: String,
        agentBody: String,
    ): Boolean {
        val isQuestion = userBody.trim().endsWith("?") || questionStarter.containsMatchIn(userBody)
        val hasMarker = markerRegex.containsMatchIn(agentBody)
        return isQuestion || hasMarker
    }

    private fun makeTitle(
        workspace: Workspace,
        userBody: String,
    ): String {
        val prefix = workspace.name.take(TITLE_PREFIX_CHARS)
        val tail =
            userBody
                .lines()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty()
                .take(TITLE_TAIL_CHARS)
        return if (tail.isBlank()) prefix else "$prefix — $tail"
    }

    private fun makeBody(
        userBody: String,
        agentBody: String,
    ): String {
        val q = userBody.trim().take(maxBodyChars / Q_FRACTION_DENOMINATOR)
        val a = agentBody.trim().take(maxBodyChars * A_FRACTION_NUMERATOR / A_FRACTION_DENOMINATOR)
        return "Q: $q\n\nA: $a"
    }

    private fun scoreFor(agentBody: String): Double {
        val markerBonus = if (markerRegex.containsMatchIn(agentBody)) MARKER_BONUS else 0.0
        val lengthBonus = (agentBody.length.coerceAtMost(LENGTH_CAP) / LENGTH_DIVISOR)
        val codeFenceBonus = if (agentBody.contains("```")) CODE_FENCE_BONUS else 0.0
        return (BASELINE + markerBonus + lengthBonus + codeFenceBonus).coerceIn(0.0, MAX_CONFIDENCE)
    }

    companion object {
        const val MIN_BODY_CHARS = 240
        const val MAX_BODY_CHARS = 4_000

        private const val TITLE_PREFIX_CHARS = 40
        private const val TITLE_TAIL_CHARS = 120
        private const val Q_FRACTION_DENOMINATOR = 4
        private const val A_FRACTION_NUMERATOR = 3
        private const val A_FRACTION_DENOMINATOR = 4

        private const val BASELINE = 0.35
        private const val MARKER_BONUS = 0.15
        private const val LENGTH_CAP = 2_000
        private const val LENGTH_DIVISOR = 4_000.0
        private const val CODE_FENCE_BONUS = 0.05
        private const val MAX_CONFIDENCE = 0.85
    }
}
