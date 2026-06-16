package com.jorisjonkers.personalstack.auth.config

import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.UUID

class ResourceServerSecurityConfigTest {
    private val converter = JwtAuthenticatedUserConverter()
    private val jwtDecoder = MutableJwtDecoder()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val authenticationProvider =
            JwtAuthenticationProvider(jwtDecoder).apply {
                setJwtAuthenticationConverter(converter)
            }
        val bearerTokenFilter = BearerTokenAuthenticationFilter(ProviderManager(authenticationProvider))

        mockMvc =
            MockMvcBuilders
                .standaloneSetup(TestController())
                .addFilters<StandaloneMockMvcBuilder>(
                    SecurityContextCleanupFilter(),
                    bearerTokenFilter,
                    ProtectedEndpointAuthorizationFilter(),
                ).build()
    }

    @Test
    fun `converter maps JWT subject username and roles to authenticated user`() {
        val userId = UUID.randomUUID()

        val authentication =
            converter.convert(
                buildJwt(
                    subject = userId.toString(),
                    username = "alice",
                    preferredUsername = "alice-preferred",
                    roles = listOf("ROLE_USER", "SERVICE_GRAFANA"),
                ),
            )

        val user = authentication.principal as AuthenticatedUser
        assertThat(user.userId).isEqualTo(userId)
        assertThat(user.username).isEqualTo("alice")
        assertThat(user.roles).containsExactly("ROLE_USER", "SERVICE_GRAFANA")
        assertThat(authentication.authorities.map { it.authority }).containsExactly("ROLE_USER", "SERVICE_GRAFANA")
    }

    @Test
    fun `converter falls back to preferred username claim`() {
        val authentication =
            converter.convert(
                buildJwt(
                    username = null,
                    preferredUsername = "alice-preferred",
                ),
            )

        val user = authentication.principal as AuthenticatedUser
        assertThat(user.username).isEqualTo("alice-preferred")
    }

    @Test
    fun `converter rejects non UUID subject`() {
        assertThatThrownBy {
            converter.convert(buildJwt(subject = "not-a-uuid"))
        }.isInstanceOf(BadCredentialsException::class.java).hasMessageContaining("JWT subject must be a user UUID")
    }

    @Test
    fun `valid bearer authenticates protected endpoint`() {
        val userId = UUID.randomUUID()
        jwtDecoder.accept(
            token = "user-token",
            jwt =
                buildJwt(
                    subject = userId.toString(),
                    username = "alice",
                    roles = listOf("ROLE_USER", "SERVICE_GRAFANA"),
                ),
        )

        mockMvc
            .perform(get("/api/v1/users/me").header("Authorization", "Bearer user-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
            .andExpect(jsonPath("$.roles[1]").value("SERVICE_GRAFANA"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["invalid-token", "expired-token"])
    fun `invalid bearer yields 401`(token: String) {
        jwtDecoder.reject(token)

        mockMvc
            .perform(get("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `public endpoint stays open without bearer`() {
        mockMvc
            .perform(get("/api/v1/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
    }

    @RestController
    private class TestController {
        @GetMapping("/api/v1/users/me")
        fun me(): Map<String, Any> {
            val user = SecurityContextHolder.getContext().authentication!!.principal as AuthenticatedUser
            return mapOf(
                "userId" to user.userId,
                "username" to user.username,
                "roles" to user.roles,
            )
        }

        @GetMapping("/api/v1/health")
        fun health(): Map<String, String> = mapOf("status" to "ok")
    }

    private class SecurityContextCleanupFilter : OncePerRequestFilter() {
        override fun doFilterInternal(
            request: HttpServletRequest,
            response: HttpServletResponse,
            filterChain: FilterChain,
        ) {
            try {
                filterChain.doFilter(request, response)
            } finally {
                SecurityContextHolder.clearContext()
            }
        }
    }

    private class ProtectedEndpointAuthorizationFilter : OncePerRequestFilter() {
        override fun doFilterInternal(
            request: HttpServletRequest,
            response: HttpServletResponse,
            filterChain: FilterChain,
        ) {
            val authenticated = SecurityContextHolder.getContext().authentication?.isAuthenticated == true
            if (!isPublic(request) && !authenticated) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                return
            }
            filterChain.doFilter(request, response)
        }

        private fun isPublic(request: HttpServletRequest): Boolean = request.requestURI == "/api/v1/health"
    }

    private class MutableJwtDecoder : JwtDecoder {
        private val acceptedTokens = mutableMapOf<String, Jwt>()
        private val rejectedTokens = mutableSetOf<String>()

        fun accept(
            token: String,
            jwt: Jwt,
        ) {
            acceptedTokens[token] = jwt
        }

        fun reject(token: String) {
            rejectedTokens += token
        }

        override fun decode(token: String): Jwt {
            if (token in rejectedTokens) throw BadJwtException("Rejected test token")
            return acceptedTokens[token] ?: throw BadJwtException("Unknown test token")
        }
    }

    companion object {
        private fun buildJwt(
            subject: String = UUID.randomUUID().toString(),
            username: String? = "alice",
            preferredUsername: String? = null,
            roles: List<String> = listOf("ROLE_USER"),
        ): Jwt {
            val builder =
                Jwt
                    .withTokenValue("test-token")
                    .header("alg", "RS256")
                    .issuer("https://auth.jorisjonkers.dev")
                    .subject(subject)
                    .issuedAt(Instant.now().minusSeconds(60))
                    .expiresAt(Instant.now().plusSeconds(300))

            username?.let { builder.claim("username", it) }
            preferredUsername?.let { builder.claim("preferred_username", it) }
            builder.claim("roles", roles)

            return builder.build()
        }
    }
}
