package com.jorisjonkers.personalstack.assistant.infrastructure.integration

import com.fasterxml.jackson.annotation.JsonProperty
import com.jorisjonkers.personalstack.assistant.config.RagProperties
import com.jorisjonkers.personalstack.assistant.domain.port.RetrievalPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * LightRAG /query in mix mode. The response shape is
 * `{ "response": "..." }` — LightRAG renders a single fused answer
 * rather than a list of snippets, so we wrap the whole answer as
 * one Snippet. The KB recall adapter complements this by returning
 * the per-note hits, which keeps the "raw snippets" channel useful
 * for the agent to expand on a specific source.
 */
@Component
class LightRagClient(
    private val restClient: RestClient,
    private val props: RagProperties,
) : RetrievalPort {
    private val log = LoggerFactory.getLogger(LightRagClient::class.java)

    /**
     * `top_k` snake-case stays on the wire because that's what
     * LightRAG's HTTP API expects; the Kotlin field uses camelCase
     * and Jackson rewrites it via @JsonProperty.
     */
    private data class Req(
        val query: String,
        val mode: String = "mix",
        @param:JsonProperty("top_k") val topK: Int = DEFAULT_TOP_K,
    )

    private data class Resp(
        val response: String?,
    )

    override fun retrieve(
        query: String,
        limit: Int,
    ): List<RetrievalPort.Snippet> {
        if (!props.enabled) return emptyList()
        return runCatching {
            val body =
                restClient
                    .post()
                    .uri("${props.lightragUrl}/query")
                    .body(Req(query = query, topK = limit))
                    .retrieve()
                    .body(Resp::class.java)
            val text = body?.response ?: ""
            if (text.isBlank()) emptyList() else listOf(RetrievalPort.Snippet("lightrag", text, 1.0))
        }.getOrElse {
            log.warn("LightRAG query failed: {}", it.message)
            emptyList()
        }
    }

    companion object {
        private const val DEFAULT_TOP_K = 5
    }
}
