package com.jorisjonkers.privatestack.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher
import java.time.Duration
import java.util.UUID

@Configuration
class AuthorizationServerConfig {
    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val authServerConfigurer = OAuth2AuthorizationServerConfigurer()
        authServerConfigurer.oidc(Customizer.withDefaults())

        http
            .securityMatcher(authServerConfigurer.endpointsMatcher)
            .with(authServerConfigurer, Customizer.withDefaults())
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .exceptionHandling { exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    LoginUrlAuthenticationEntryPoint("/login"),
                    MediaTypeRequestMatcher(MediaType.TEXT_HTML),
                )
            }

        return http.build()
    }

    @Bean
    fun registeredClientRepository(): RegisteredClientRepository =
        InMemoryRegisteredClientRepository(buildAuthUiClient(), buildAssistantApiClient())

    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings =
        AuthorizationServerSettings
            .builder()
            .issuer("https://auth.jorisjonkers.dev")
            .build()

    private fun buildAuthUiClient(): RegisteredClient =
        RegisteredClient
            .withId(UUID.randomUUID().toString())
            .clientId("auth-ui")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://auth.jorisjonkers.dev/callback")
            .redirectUri("http://localhost:5174/callback")
            .postLogoutRedirectUri("https://auth.jorisjonkers.dev/logged-out")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .clientSettings(
                ClientSettings
                    .builder()
                    .requireProofKey(true)
                    .requireAuthorizationConsent(false)
                    .build(),
            ).tokenSettings(
                TokenSettings
                    .builder()
                    .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                    .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
                    .reuseRefreshTokens(false)
                    .build(),
            ).build()

    private fun buildAssistantApiClient(): RegisteredClient =
        RegisteredClient
            .withId(UUID.randomUUID().toString())
            .clientId("assistant-api")
            .clientSecret("{noop}assistant-secret") // Production: load from Vault
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("api.read")
            .scope("api.write")
            .tokenSettings(
                TokenSettings
                    .builder()
                    .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                    .build(),
            ).build()

    companion object {
        private val ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(15)
        private val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(7)
    }
}
