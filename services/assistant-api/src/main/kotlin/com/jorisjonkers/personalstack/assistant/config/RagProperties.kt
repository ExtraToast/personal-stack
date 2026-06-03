package com.jorisjonkers.personalstack.assistant.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rag")
data class RagProperties(
    val enabled: Boolean = true,
    // Sensible cluster-default URLs make the integration-test boot
    // path work without needing every test class to wire dynamic
    // properties for the RAG feature. Production application.yml
    // overrides these via env vars.
    val knowledgeMcpUrl: String = "http://knowledge-api.knowledge-system.svc.cluster.local:8080",
    val knowledgeMcpToken: String = "",
    val lightragUrl: String = "http://lightrag.knowledge-system.svc.cluster.local:9621",
    val maxSnippets: Int = 5,
    val maxContextChars: Int = 4_000,
    // Hits below this score are dropped before injection. Keeps the
    // context envelope from growing on marginal matches.
    val minScore: Double = 0.3,
    // Passed to knowledge.recall as the `mode` parameter. `deep` runs
    // hybrid retrieval + listwise reranker on the knowledge-api side.
    val recallMode: String = "deep",
)
