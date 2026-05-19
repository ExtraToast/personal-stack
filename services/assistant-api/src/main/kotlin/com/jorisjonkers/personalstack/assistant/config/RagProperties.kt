package com.jorisjonkers.personalstack.assistant.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rag")
data class RagProperties(
    val enabled: Boolean = true,
    val knowledgeMcpUrl: String,
    val knowledgeMcpToken: String = "",
    val lightragUrl: String,
    val maxSnippets: Int = 5,
    val maxContextChars: Int = 4_000,
)
