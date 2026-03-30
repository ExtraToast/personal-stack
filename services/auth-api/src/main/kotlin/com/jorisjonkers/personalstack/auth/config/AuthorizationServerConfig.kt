package com.jorisjonkers.personalstack.auth.config

import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
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
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.UUID

private fun currentAuthentication(): Authentication? =
    SecurityContextHolder
        .getContext()
        .authentication

private fun isAnonymousOrUnauthenticated(authentication: Authentication?): Boolean =
    authentication == null ||
        authentication is AnonymousAuthenticationToken ||
        !authentication.isAuthenticated

private fun hasClientAccess(
    authentication: Authentication?,
    requiredPermission: ServicePermission,
): Boolean {
    if (authentication == null) return false
    val authorities = authentication.authorities.map { it.authority }.toSet()
    return "ROLE_ADMIN" in authorities || "SERVICE_${requiredPermission.name}" in authorities
}

@Configuration
class AuthorizationServerConfig(
    @param:Value("\${auth.issuer:https://auth.jorisjonkers.dev}")
    private val issuer: String,
    @param:Value("\${auth.login-url:http://localhost:5174/login}")
    private val loginUrl: String,
    @param:Value("\${auth.clients.grafana.secret:grafana-secret}")
    private val grafanaClientSecret: String,
    @param:Value("\${auth.clients.n8n.secret:n8n-secret}")
    private val n8nClientSecret: String,
    @param:Value("\${auth.clients.vault.secret:vault-secret}")
    private val vaultClientSecret: String,
) {
    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(
        http: HttpSecurity,
        corsConfigurationSource: CorsConfigurationSource,
    ): SecurityFilterChain {
        val authServerConfigurer = OAuth2AuthorizationServerConfigurer()
        authServerConfigurer.oidc(Customizer.withDefaults())

        val oauthEndpoints =
            OrRequestMatcher(
                PathPatternRequestMatcher.pathPattern("/api/oauth2/**"),
                PathPatternRequestMatcher.pathPattern("/api/userinfo"),
                PathPatternRequestMatcher.pathPattern("/api/connect/logout"),
                PathPatternRequestMatcher.pathPattern("/.well-known/**"),
            )

        http
            .securityMatcher(oauthEndpoints)
            .cors { it.configurationSource(corsConfigurationSource) }
            .with(authServerConfigurer, Customizer.withDefaults())
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .addFilterAfter(downstreamClientAuthorizationFilter(), SecurityContextHolderFilter::class.java)
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
        InMemoryRegisteredClientRepository(
            buildAuthUiClient(),
            buildAppUiClient(),
            buildAssistantApiClient(),
            buildGrafanaClient(),
            buildN8nClient(),
            buildRabbitMqClient(),
            buildVaultClient(),
        )

    @Bean
    fun authorizationService(
        jdbcOperations: JdbcOperations,
        registeredClientRepository: RegisteredClientRepository,
    ): OAuth2AuthorizationService = JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository)

    @Bean
    fun authorizationConsentService(
        jdbcOperations: JdbcOperations,
        registeredClientRepository: RegisteredClientRepository,
    ): OAuth2AuthorizationConsentService =
        JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository)

    @Bean
    fun jwtTokenCustomizer(userRepository: UserRepository): OAuth2TokenCustomizer<JwtEncodingContext> =
        OAuth2TokenCustomizer { context ->
            val principal = context.getPrincipal<Authentication>()
            val credentials =
                userRepository.findCredentialsByUsername(principal.name) ?: return@OAuth2TokenCustomizer
            val roles = buildRoles(credentials)

            when {
                context.tokenType == OAuth2TokenType.ACCESS_TOKEN -> {
                    context.claims.claim("roles", roles)
                    context.claims.claim("username", credentials.username)
                    context.claims.claim("preferred_username", credentials.username)
                    context.claims.claim("email", credentials.email)
                    context.claims.claim("aud", listOf(context.registeredClient.clientId))
                    context.claims.subject(credentials.userId.value.toString())
                }

                context.tokenType.value == OidcParameterNames.ID_TOKEN -> {
                    context.claims.claim("roles", roles)
                    context.claims.subject(credentials.userId.value.toString())

                    if (OidcScopes.PROFILE in context.authorizedScopes) {
                        context.claims.claim("preferred_username", credentials.username)
                        context.claims.claim("name", credentials.username)
                    }

                    if (OidcScopes.EMAIL in context.authorizedScopes) {
                        context.claims.claim("email", credentials.email)
                        context.claims.claim("email_verified", credentials.emailConfirmed)
                    }
                }
            }
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
            .withId(deterministicId("auth-ui"))
            .clientId("auth-ui")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://auth.jorisjonkers.dev/callback")
            .redirectUri("https://auth.jorisjonkers.test/callback")
            .redirectUri("http://localhost:5174/callback")
            .postLogoutRedirectUri("https://auth.jorisjonkers.dev/logged-out")
            .postLogoutRedirectUri("https://auth.jorisjonkers.test/logged-out")
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
            .withId(deterministicId("app-ui"))
            .clientId("app-ui")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://jorisjonkers.dev/callback")
            .redirectUri("https://jorisjonkers.test/callback")
            .redirectUri("http://localhost:5175/callback")
            .postLogoutRedirectUri("https://jorisjonkers.dev")
            .postLogoutRedirectUri("https://jorisjonkers.test")
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
            .withId(deterministicId("assistant-api"))
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

    private fun buildGrafanaClient(): RegisteredClient =
        RegisteredClient
            .withId(deterministicId("grafana"))
            .clientId("grafana")
            .clientSecret("{noop}$grafanaClientSecret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://grafana.jorisjonkers.dev/login/generic_oauth")
            .redirectUri("https://grafana.jorisjonkers.test/login/generic_oauth")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .clientSettings(
                ClientSettings
                    .builder()
                    .requireProofKey(false)
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

    private fun buildN8nClient(): RegisteredClient =
        RegisteredClient
            .withId(deterministicId("n8n"))
            .clientId("n8n")
            .clientSecret("{noop}$n8nClientSecret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://n8n.jorisjonkers.dev/auth/oidc/callback")
            .redirectUri("https://n8n.jorisjonkers.test/auth/oidc/callback")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .clientSettings(
                ClientSettings
                    .builder()
                    .requireProofKey(false)
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

    private fun buildRabbitMqClient(): RegisteredClient =
        RegisteredClient
            .withId(deterministicId("rabbitmq"))
            .clientId("rabbitmq")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://rabbitmq.jorisjonkers.dev/js/oidc-oauth/login-callback.html")
            .redirectUri("https://rabbitmq.jorisjonkers.test/js/oidc-oauth/login-callback.html")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .scope("rabbitmq.tag:administrator")
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

    private fun buildVaultClient(): RegisteredClient =
        RegisteredClient
            .withId(deterministicId("vault"))
            .clientId("vault")
            .clientSecret("{noop}$vaultClientSecret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://vault.jorisjonkers.dev/ui/vault/auth/oidc/oidc/callback")
            .redirectUri("https://vault.jorisjonkers.test/ui/vault/auth/oidc/oidc/callback")
            .redirectUri("http://localhost:8250/oidc/callback")
            .redirectUri("http://127.0.0.1:8250/oidc/callback")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .clientSettings(
                ClientSettings
                    .builder()
                    .requireProofKey(false)
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

    private fun buildRoles(credentials: UserCredentials): List<String> =
        buildList {
            add("ROLE_${credentials.role.name}")
            if (credentials.role == Role.ADMIN) {
                addAll(ServicePermission.entries.map { "SERVICE_${it.name}" })
            } else {
                addAll(credentials.servicePermissions.map { "SERVICE_${it.name}" })
            }
        }

    private fun downstreamClientAuthorizationFilter(): OncePerRequestFilter =
        object : OncePerRequestFilter() {
            override fun shouldNotFilter(request: HttpServletRequest): Boolean =
                request.requestURI != "/api/oauth2/authorize"

            override fun doFilterInternal(
                request: HttpServletRequest,
                response: HttpServletResponse,
                filterChain: FilterChain,
            ) {
                val requiredPermission =
                    downstreamClientPermissions[request.getParameter("client_id")] ?: run {
                        filterChain.doFilter(request, response)
                        return
                    }
                val authentication = currentAuthentication()

                if (isAnonymousOrUnauthenticated(authentication)) {
                    filterChain.doFilter(request, response)
                    return
                }

                if (!hasClientAccess(authentication, requiredPermission)) {
                    response.sendError(
                        HttpServletResponse.SC_FORBIDDEN,
                        "Access denied for OAuth client",
                    )
                    return
                }

                filterChain.doFilter(request, response)
            }
        }

    companion object {
        private val ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(15)
        private val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(7)

        private fun deterministicId(clientId: String): String =
            UUID.nameUUIDFromBytes(clientId.toByteArray()).toString()

        private val downstreamClientPermissions: Map<String, ServicePermission> =
            mapOf(
                "grafana" to ServicePermission.GRAFANA,
                "vault" to ServicePermission.VAULT,
                "n8n" to ServicePermission.N8N,
                "rabbitmq" to ServicePermission.RABBITMQ,
            )
    }
}
