package com.jorisjonkers.personalstack.assistant.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Assistant API")
                    .description("Conversation and messaging service for jorisjonkers.dev")
                    .version("1.0.0"),
            ).addServersItem(Server().url("https://assistant.jorisjonkers.dev").description("Production"))
            .addServersItem(Server().url("http://localhost:8082").description("Local development"))
            .components(
                Components()
                    .addSecuritySchemes(
                        "xUserId",
                        SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .`in`(SecurityScheme.In.HEADER)
                            .name("X-User-Id")
                            .description("User identifier injected by Traefik forward-auth"),
                    ),
            ).addSecurityItem(SecurityRequirement().addList("xUserId"))
}
