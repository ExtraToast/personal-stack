package com.jorisjonkers.personalstack.assistant.application.rag

import com.jorisjonkers.personalstack.assistant.config.RagProperties
import com.jorisjonkers.personalstack.assistant.domain.port.RetrievalPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContextBuilderTest {
    private fun props(enabled: Boolean = true, maxSnippets: Int = 5, maxContextChars: Int = 4_000): RagProperties =
        RagProperties(
            enabled = enabled,
            knowledgeMcpUrl = "http://kb:8080",
            knowledgeMcpToken = "",
            lightragUrl = "http://lightrag:9621",
            maxSnippets = maxSnippets,
            maxContextChars = maxContextChars,
        )

    private class FakeSource(private val snippets: List<RetrievalPort.Snippet>) : RetrievalPort {
        override fun retrieve(query: String, limit: Int): List<RetrievalPort.Snippet> = snippets
    }

    @Test
    fun `augment returns plain prompt when RAG disabled`() {
        val builder = ContextBuilder(emptyList(), props(enabled = false))
        assertThat(builder.augment("hi")).isEqualTo("hi")
    }

    @Test
    fun `augment returns plain prompt when no sources hit`() {
        val builder = ContextBuilder(listOf(FakeSource(emptyList())), props())
        assertThat(builder.augment("hi")).isEqualTo("hi")
    }

    @Test
    fun `augment wraps merged sorted snippets in a context envelope`() {
        val builder =
            ContextBuilder(
                listOf(
                    FakeSource(listOf(RetrievalPort.Snippet("kb:a", "first", 0.9))),
                    FakeSource(listOf(RetrievalPort.Snippet("lightrag", "second", 0.5))),
                ),
                props(),
            )
        val out = builder.augment("question")
        assertThat(out).startsWith("<context source=\"personal-stack-rag\">")
        assertThat(out).contains("[kb:a] first")
        assertThat(out).contains("[lightrag] second")
        assertThat(out).contains("question")
        // Higher-score snippet comes first
        assertThat(out.indexOf("first")).isLessThan(out.indexOf("second"))
    }

    @Test
    fun `augment dedupes snippets with identical text`() {
        val builder =
            ContextBuilder(
                listOf(
                    FakeSource(listOf(RetrievalPort.Snippet("kb:a", "same", 0.9))),
                    FakeSource(listOf(RetrievalPort.Snippet("kb:b", "same", 0.8))),
                ),
                props(),
            )
        val out = builder.augment("q")
        val count = out.split("same").size - 1
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `augment respects maxContextChars`() {
        val long = "x".repeat(900)
        val builder =
            ContextBuilder(
                listOf(
                    FakeSource(
                        (1..10).map { RetrievalPort.Snippet("kb:$it", long, 1.0 - it * 0.01) },
                    ),
                ),
                props(maxSnippets = 10, maxContextChars = 1_500),
            )
        val out = builder.augment("q")
        // At most one full snippet fits before the budget runs out (~900 chars per chunk).
        val count = out.split("[kb:").size - 1
        assertThat(count).isLessThanOrEqualTo(2)
    }
}
