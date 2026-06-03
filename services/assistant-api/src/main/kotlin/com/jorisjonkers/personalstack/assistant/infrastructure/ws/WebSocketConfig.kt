package com.jorisjonkers.personalstack.assistant.infrastructure.ws

import org.springframework.boot.tomcat.TomcatContextCustomizer
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

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
     * terminal) and the bridge relays large outbound PTY frames. Tomcat's
     * default 8 KiB text buffer closes the session with 1009 (TOO_BIG)
     * once a frame exceeds it, so raise the server-side incoming buffer
     * to hold the largest frame the terminal stream produces.
     *
     * The buffer is set through Tomcat's context init parameters rather
     * than a `ServletServerContainerFactoryBean`: that bean resolves the
     * `ServerContainer` from the live `ServletContext` at creation time
     * and so fails to start under the MockServletContext used by
     * `@SpringBootTest`. This customizer only runs against a real
     * embedded Tomcat, leaving the test contexts untouched.
     */
    @Bean
    fun webSocketBufferCustomizer(): WebServerFactoryCustomizer<TomcatServletWebServerFactory> =
        WebServerFactoryCustomizer { factory ->
            factory.addContextCustomizers(
                TomcatContextCustomizer { context ->
                    context.addParameter("org.apache.tomcat.websocket.textBufferSize", MAX_TEXT_BUFFER_BYTES.toString())
                    context.addParameter(
                        "org.apache.tomcat.websocket.binaryBufferSize",
                        MAX_TEXT_BUFFER_BYTES.toString(),
                    )
                },
            )
        }

    private companion object {
        private const val MAX_TEXT_BUFFER_BYTES = 1 shl 20
    }
}
