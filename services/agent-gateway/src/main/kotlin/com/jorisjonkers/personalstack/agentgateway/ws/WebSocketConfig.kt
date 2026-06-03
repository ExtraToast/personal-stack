package com.jorisjonkers.personalstack.agentgateway.ws

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val agentAttachHandler: AgentAttachHandler,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(agentAttachHandler, "/ws/agents/*/attach")
            .setAllowedOriginPatterns("*")
    }

    /**
     * A paste relayed from the browser arrives as one inbound text
     * frame and can exceed the default 8 KiB buffer, which would close
     * the attach with 1009 (TOO_BIG). Match the buffer the bridge uses
     * so the gateway never drops the attach on a large keystroke frame.
     */
    @Bean
    fun webSocketContainer(): ServletServerContainerFactoryBean =
        ServletServerContainerFactoryBean().apply {
            setMaxTextMessageBufferSize(MAX_TEXT_BUFFER_BYTES)
            setMaxBinaryMessageBufferSize(MAX_TEXT_BUFFER_BYTES)
            setMaxSessionIdleTimeout(IDLE_TIMEOUT_MS)
        }

    private companion object {
        private const val MAX_TEXT_BUFFER_BYTES = 1 shl 20
        private const val IDLE_TIMEOUT_MS = 30L * 60 * 1000
    }
}
