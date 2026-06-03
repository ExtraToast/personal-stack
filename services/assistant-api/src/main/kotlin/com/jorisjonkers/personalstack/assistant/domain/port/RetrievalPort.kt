package com.jorisjonkers.personalstack.assistant.domain.port

/**
 * Driven port: "give me snippets relevant to this prompt." The
 * application doesn't care whether they came from LightRAG, FTS, or
 * a static vector store — only the final list is exposed. Adapters
 * fan out to the underlying sources and rerank as needed.
 */
interface RetrievalPort {
    data class Snippet(
        val source: String,
        val text: String,
        val score: Double,
        val id: String? = null,
    )

    fun retrieve(
        query: String,
        limit: Int,
    ): List<Snippet>
}

/**
 * Driven port for capturing what an agent learned. Right now we
 * ingest the user's prompt + agent's turn as a single note; the
 * Block protocol will eventually let us emit a structured "lesson"
 * block that lands here directly.
 */
interface KnowledgeWritePort {
    fun ingestNote(
        title: String,
        body: String,
        scope: String,
        tags: List<String> = emptyList(),
    )
}
