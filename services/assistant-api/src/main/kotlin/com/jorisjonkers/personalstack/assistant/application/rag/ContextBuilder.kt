package com.jorisjonkers.personalstack.assistant.application.rag

import com.jorisjonkers.personalstack.assistant.config.RagProperties
import com.jorisjonkers.personalstack.assistant.domain.port.RetrievalPort
import org.springframework.stereotype.Component

/**
 * Aggregates retrieval results from every RetrievalPort bean,
 * dedupes by snippet text, ranks by score, and formats into a
 * single `<context>...</context>` envelope that the
 * SendUserInputCommandHandler prepends to the agent's input.
 *
 * Why a single envelope rather than per-source headers: every CLI
 * we plug in has its own quirks for parsing user input. A single
 * fenced region with consistent markers is the cheapest "ignore if
 * you don't understand it" surface.
 */
@Component
class ContextBuilder(
    private val sources: List<RetrievalPort>,
    private val props: RagProperties,
) {
    fun augment(userPrompt: String): String {
        if (!props.enabled || sources.isEmpty()) return userPrompt
        val merged =
            sources
                .flatMap { it.retrieve(userPrompt, props.maxSnippets) }
                .sortedByDescending { it.score }
                .distinctBy { it.text }
                .take(props.maxSnippets)
        if (merged.isEmpty()) return userPrompt
        val body =
            buildString {
                append("<context source=\"personal-stack-rag\">\n")
                var used = 0
                for (s in merged) {
                    val chunk = "[${s.source}] ${s.text.take(800)}\n"
                    if (used + chunk.length > props.maxContextChars) break
                    append(chunk)
                    used += chunk.length
                }
                append("</context>\n\n")
            }
        return body + userPrompt
    }
}
