package com.jorisjonkers.personalstack.assistant.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableAsync
@EnableScheduling
class AsyncConfig {
    /**
     * Fallback ObjectMapper for components that need JSON but are
     * out of the Spring-MVC auto-configuration scope (the WebSocket
     * starter doesn't bring JacksonAutoConfiguration with it on its
     * own under @SpringBootTest in this module). Marked
     * `@ConditionalOnMissingBean` semantics via @Qualifier so the
     * Boot-provided one wins when both exist.
     */
    @Bean
    @Qualifier("assistantApiObjectMapper")
    fun assistantApiObjectMapper(): ObjectMapper = jacksonObjectMapper()
}
