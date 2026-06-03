package com.jorisjonkers.personalstack.assistant.infrastructure.ws

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val sessionAttachHandler: SessionAttachHandler,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(sessionAttachHandler, "/api/v1/ws/sessions/*/attach")
            .setAllowedOriginPatterns("*")
    }

    /**
     * The browser relays large inbound frames (pastes into the agent
     * terminal) and the bridge relays large outbound PTY frames. The
     * default 8 KiB text buffer closes the session with 1009 (TOO_BIG)
     * once a frame exceeds it, so raise it to hold the largest frame the
     * terminal stream produces. The idle timeout is a backstop above the
     * client's 30 s heartbeat so a dead socket is eventually reaped
     * without cutting a live, heartbeating session.
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
