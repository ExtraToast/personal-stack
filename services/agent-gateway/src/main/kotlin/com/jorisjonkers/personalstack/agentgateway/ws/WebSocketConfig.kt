package com.jorisjonkers.personalstack.agentgateway.ws

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
    private val agentAttachHandler: AgentAttachHandler,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(agentAttachHandler, "/ws/agents/*/attach")
            .setAllowedOriginPatterns("*")
    }

    /**
     * A paste relayed from the browser arrives as one inbound text frame
     * and can exceed Tomcat's default 8 KiB buffer, which would close the
     * attach with 1009 (TOO_BIG). Raise the server-side incoming buffer
     * to match the bridge so the gateway never drops the attach on a
     * large keystroke frame.
     *
     * Set through Tomcat's context init parameters rather than a
     * `ServletServerContainerFactoryBean`, which fails to start under the
     * MockServletContext used by `@SpringBootTest`; this customizer only
     * runs against a real embedded Tomcat.
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
