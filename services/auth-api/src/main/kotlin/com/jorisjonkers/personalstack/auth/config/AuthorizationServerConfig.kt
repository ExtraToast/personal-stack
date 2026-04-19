package com.jorisjonkers.personalstack.auth.config

import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
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
import org.springframework.security.jackson.SecurityJacksonModules
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.jackson.OAuth2AuthorizationServerJacksonModule
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
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
    @param:Value("\${auth.clients.headlamp.secret:headlamp-secret}")
    private val headlampClientSecret: String,
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
            buildGrafanaClient(grafanaClientSecret),
            buildN8nClient(n8nClientSecret),
            buildRabbitMqClient(),
            buildVaultClient(vaultClientSecret),
            buildHeadlampClient(headlampClientSecret),
        )

    @Bean
    fun authorizationService(
        jdbcOperations: JdbcOperations,
        registeredClientRepository: RegisteredClientRepository,
    ): OAuth2AuthorizationService {
        val jsonMapper = buildAuthorizationJsonMapper()
        val rowMapper =
            JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(
                registeredClientRepository,
                jsonMapper,
            )
        return JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository).apply {
            setAuthorizationRowMapper(rowMapper)
        }
    }

    private fun buildAuthorizationJsonMapper(): tools.jackson.databind.json.JsonMapper {
        val classLoader = AuthorizationServerConfig::class.java.classLoader
        val typeValidator =
            tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
                .builder()
                .allowIfSubType("com.jorisjonkers.personalstack.")
                .allowIfSubType("kotlin.")
        return tools.jackson.databind.json.JsonMapper
            .builder()
            .addModules(SecurityJacksonModules.getModules(classLoader, typeValidator))
            .addModule(OAuth2AuthorizationServerJacksonModule())
            .addModule(
                tools.jackson.module.kotlin.KotlinModule
                    .Builder()
                    .build(),
            ).addMixIn(AuthenticatedUser::class.java, AuthenticatedUserMixin::class.java)
            .addMixIn(UserId::class.java, UserIdMixin::class.java)
            .build()
    }

    @Bean
    fun authorizationConsentService(
        jdbcOperations: JdbcOperations,
        registeredClientRepository: RegisteredClientRepository,
    ): OAuth2AuthorizationConsentService =
        JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository)

    @Bean
    @Suppress("LongMethod")
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
                        context.claims.claim("name", "${credentials.firstName} ${credentials.lastName}")
                    }

                    if (OidcScopes.EMAIL in context.authorizedScopes) {
                        context.claims.claim("email", credentials.email)
                        context.claims.claim("email_verified", credentials.emailConfirmed)
                    }

                    val k8sGroups = kubernetesGroups(credentials)
                    if (k8sGroups.isNotEmpty() && "groups" in context.authorizedScopes) {
                        context.claims.claim("groups", k8sGroups)
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

    private fun buildRoles(credentials: UserCredentials): List<String> =
        buildList {
            add("ROLE_${credentials.role.name}")
            if (credentials.role == Role.ADMIN) {
                addAll(ServicePermission.entries.map { "SERVICE_${it.name}" })
            } else {
                addAll(credentials.servicePermissions.map { "SERVICE_${it.name}" })
            }
        }

    // Kubernetes group membership for ID tokens issued to OIDC clients that
    // talk to the k3s API server (Headlamp). The k3s control plane runs
    // --oidc-groups-claim=groups --oidc-groups-prefix=oidc: and we bind
    // oidc:k8s-admin to the cluster-admin ClusterRole. DASHBOARD permission
    // therefore grants full cluster management, matching how the host-level
    // forward-auth already gates access to dashboard.jorisjonkers.dev.
    private fun kubernetesGroups(credentials: UserCredentials): List<String> =
        if (credentials.role == Role.ADMIN ||
            ServicePermission.DASHBOARD in credentials.servicePermissions
        ) {
            listOf("k8s-admin")
        } else {
            emptyList()
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
                    DOWNSTREAM_CLIENT_PERMISSIONS[request.getParameter("client_id")] ?: run {
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
        private val DOWNSTREAM_CLIENT_PERMISSIONS: Map<String, ServicePermission> =
            mapOf(
                "grafana" to ServicePermission.GRAFANA,
                "vault" to ServicePermission.VAULT,
                "n8n" to ServicePermission.N8N,
                "headlamp" to ServicePermission.DASHBOARD,
                "rabbitmq" to ServicePermission.RABBITMQ,
            )
    }
}
