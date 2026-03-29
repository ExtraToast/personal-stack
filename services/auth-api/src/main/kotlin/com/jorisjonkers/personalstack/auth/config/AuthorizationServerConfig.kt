package com.jorisjonkers.personalstack.auth.config

import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.AndRequestMatcher
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.web.cors.CorsConfigurationSource
import java.time.Duration
import java.util.UUID

@Configuration
class AuthorizationServerConfig(
    @param:Value("\${auth.issuer:https://auth.jorisjonkers.dev}")
    private val issuer: String,
    @param:Value("\${auth.login-url:http://localhost:5174/login}")
    private val loginUrl: String,
) {
    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(
        http: HttpSecurity,
        corsConfigurationSource: CorsConfigurationSource,
    ): SecurityFilterChain {
        val authServerConfigurer = OAuth2AuthorizationServerConfigurer()
        authServerConfigurer.oidc(Customizer.withDefaults())

        val healthMatcher =
            OrRequestMatcher(
                PathPatternRequestMatcher.pathPattern("/api/actuator/health"),
                PathPatternRequestMatcher.pathPattern("/api/actuator/info"),
                PathPatternRequestMatcher.pathPattern("/api/v1/health"),
            )
        val matcher = AndRequestMatcher(authServerConfigurer.endpointsMatcher, NegatedRequestMatcher(healthMatcher))

        http
            .securityMatcher(matcher)
            .cors { it.configurationSource(corsConfigurationSource) }
            .with(authServerConfigurer, Customizer.withDefaults())
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .securityContext { ctx ->
                ctx.securityContextRepository(HttpSessionSecurityContextRepository())
            }.exceptionHandling { exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    LoginUrlAuthenticationEntryPoint(loginUrl),
                    MediaTypeRequestMatcher(MediaType.TEXT_HTML),
                )
            }

        return http.build()
    }

    @Bean
    fun registeredClientRepository(): RegisteredClientRepository =
        InMemoryRegisteredClientRepository(buildAuthUiClient(), buildAppUiClient(), buildAssistantApiClient())

    @Bean
    fun jwtTokenCustomizer(userRepository: UserRepository): OAuth2TokenCustomizer<JwtEncodingContext> =
        OAuth2TokenCustomizer { context ->
            if (context.tokenType != OAuth2TokenType.ACCESS_TOKEN) return@OAuth2TokenCustomizer
            val principal = context.getPrincipal<Authentication>()
            val credentials =
                userRepository.findCredentialsByUsername(principal.name) ?: return@OAuth2TokenCustomizer
            val roles =
                buildList {
                    add("ROLE_${credentials.role.name}")
                    if (credentials.role == Role.ADMIN) {
                        addAll(ServicePermission.entries.map { "SERVICE_${it.name}" })
                    } else {
                        addAll(credentials.servicePermissions.map { "SERVICE_${it.name}" })
                    }
                }
            context.claims.claim("roles", roles)
            context.claims.claim("username", credentials.username)
            context.claims.subject(credentials.userId.value.toString())
        }

    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings =
        AuthorizationServerSettings
            .builder()
            .issuer(issuer)
            .authorizationEndpoint("/api/oauth2/authorize")
            .tokenEndpoint("/api/oauth2/token")
            .jwkSetEndpoint("/api/oauth2/jwks")
            .tokenRevocationEndpoint("/api/oauth2/revoke")
            .tokenIntrospectionEndpoint("/api/oauth2/introspect")
            .oidcUserInfoEndpoint("/api/userinfo")
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

    private fun buildAppUiClient(): RegisteredClient =
        RegisteredClient
            .withId(UUID.randomUUID().toString())
            .clientId("app-ui")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://jorisjonkers.dev/callback")
            .redirectUri("http://localhost:5175/callback")
            .postLogoutRedirectUri("https://jorisjonkers.dev")
            .postLogoutRedirectUri("http://localhost:5175")
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
