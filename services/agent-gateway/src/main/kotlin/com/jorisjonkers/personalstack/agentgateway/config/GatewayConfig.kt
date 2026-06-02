package com.jorisjonkers.personalstack.agentgateway.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(GatewayProperties::class)
class GatewayConfig {
    // Spring Boot 4 auto-configures a Jackson 3 (tools.jackson) ObjectMapper,
    // but AgentAttachHandler injects the Jackson 2 com.fasterxml ObjectMapper
    // the rest of the monorepo uses, and no bean of that type exists otherwise
    // — the context failed to start without this. The WS envelope is plain
    // Map<->JSON, so the bare mapper (no Kotlin module) is sufficient.
    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper()
}
