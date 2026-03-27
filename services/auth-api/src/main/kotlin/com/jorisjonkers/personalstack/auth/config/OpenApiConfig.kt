package com.jorisjonkers.personalstack.auth.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.OAuthFlows
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
                    .title("Auth API")
                    .description("Authentication and authorization service for jorisjonkers.dev")
                    .version("1.0.0"),
            ).addServersItem(Server().url("https://auth.jorisjonkers.dev").description("Production"))
            .addServersItem(Server().url("http://localhost:8081").description("Local development"))
            .components(buildSecurityComponents())
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))

    private fun buildSecurityComponents(): Components =
        Components()
            .addSecuritySchemes(
                "oauth2",
                SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .flows(
                        OAuthFlows().authorizationCode(
                            OAuthFlow()
                                .authorizationUrl("https://auth.jorisjonkers.dev/oauth2/authorize")
                                .tokenUrl("https://auth.jorisjonkers.dev/oauth2/token")
                                .refreshUrl("https://auth.jorisjonkers.dev/oauth2/token"),
                        ),
                    ),
            ).addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"),
            )
}
