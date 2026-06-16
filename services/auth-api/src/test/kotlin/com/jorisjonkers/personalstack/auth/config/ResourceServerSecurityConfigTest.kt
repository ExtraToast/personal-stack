package com.jorisjonkers.personalstack.auth.config

import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [ResourceServerSecurityConfigTest.TestController::class])
@Import(SecurityConfig::class, ResourceServerSecurityConfigTest.TestBeans::class)
class ResourceServerSecurityConfigTest(
    private val mockMvc: MockMvc,
    private val jwtDecoder: MutableJwtDecoder,
) {
    @Test
    fun `valid bearer authenticates users me`() {
        jwtDecoder.accept("user-token", roles = listOf("ROLE_USER", "SERVICE_GRAFANA"))

        mockMvc
            .perform(get("/api/v1/users/me").header("Authorization", "Bearer user-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
    }

    @Test
    fun `valid bearer authenticates totp endpoints without csrf`() {
        jwtDecoder.accept("user-token", roles = listOf("ROLE_USER"))

        mockMvc
            .perform(post("/api/v1/totp/enroll").header("Authorization", "Bearer user-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("alice"))

        mockMvc
            .perform(post("/api/v1/totp/verify").header("Authorization", "Bearer user-token"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `valid admin bearer authenticates admin users`() {
        jwtDecoder.accept("admin-token", username = "admin", roles = listOf("ROLE_ADMIN"))

        mockMvc
            .perform(get("/api/v1/admin/users").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
    }

    @Test
    fun `valid bearer authenticates change password without csrf`() {
        jwtDecoder.accept("user-token", roles = listOf("ROLE_USER"))

        mockMvc
            .perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer user-token"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `session authentication still authenticates protected endpoints`() {
        val session = authenticatedSession(roles = listOf("ROLE_ADMIN"))

        mockMvc
            .perform(get("/api/v1/users/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("session-user"))

        mockMvc
            .perform(get("/api/v1/admin/users").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("session-user"))

        mockMvc
            .perform(post("/api/v1/totp/enroll").session(session).with(csrf()))
            .andExpect(status().isOk)

        mockMvc
            .perform(post("/api/v1/totp/verify").session(session).with(csrf()))
            .andExpect(status().isNoContent)

        mockMvc
            .perform(post("/api/v1/auth/change-password").session(session).with(csrf()))
            .andExpect(status().isNoContent)
    }

    @ParameterizedTest
    @MethodSource("rejectedBearerTokens")
    fun `rejected bearer yields 401`(token: String) {
        jwtDecoder.reject(token)

        mockMvc
            .perform(get("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
    }

    @ParameterizedTest
    @MethodSource("publicEndpoints")
    fun `public endpoints remain reachable unauthenticated`(request: PublicRequest) {
        mockMvc
            .perform(request.builder)
            .andExpect(status().isOk)
    }

    private fun authenticatedSession(roles: List<String>): org.springframework.mock.web.MockHttpSession {
        val user =
            AuthenticatedUser.of(
                userId = UserId(UUID.randomUUID()),
                username = "session-user",
                roles = roles,
            )
        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val session = org.springframework.mock.web.MockHttpSession()
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            SecurityContextImpl(authentication),
        )
        return session
    }

    @RestController
    class TestController {
        @GetMapping("/api/v1/users/me")
        fun me(
            @AuthenticationPrincipal user: AuthenticatedUser,
        ) = principalBody(user)

        @PostMapping("/api/v1/totp/enroll")
        fun enroll(
            @AuthenticationPrincipal user: AuthenticatedUser,
        ) = principalBody(user)

        @PostMapping("/api/v1/totp/verify")
        fun verify(): ResponseEntity<Void> = ResponseEntity.noContent().build()

        @GetMapping("/api/v1/admin/users")
        @PreAuthorize("hasRole('ADMIN')")
        fun admin(
            @AuthenticationPrincipal user: AuthenticatedUser,
        ) = principalBody(user)

        @PostMapping("/api/v1/auth/change-password")
        fun changePassword(): ResponseEntity<Void> = ResponseEntity.noContent().build()

        @PostMapping(
            "/api/v1/users/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/resend-confirmation",
            "/api/v1/auth/session-login",
            "/api/v1/auth/totp-challenge",
        )
        fun publicPost(): Map<String, String> = mapOf("status" to "ok")

        @GetMapping(
            "/api/v1/auth/confirm-email",
            "/api/v1/health",
            "/api/v1/api-docs/openapi.json",
            "/api/v1/swagger-ui/index.html",
        )
        fun publicGet(): Map<String, String> = mapOf("status" to "ok")

        private fun principalBody(user: AuthenticatedUser): Map<String, Any> =
            mapOf(
                "username" to user.username,
                "roles" to user.roles,
            )
    }

    @TestConfiguration
    class TestBeans {
        @Bean
        fun jwtDecoder(): MutableJwtDecoder = MutableJwtDecoder()

        @Bean
        fun userDetailsService(): UserDetailsService =
            UserDetailsService { username: String ->
                AuthenticatedUser.of(
                    userId = UserId(UUID.randomUUID()),
                    username = username,
                    roles = listOf("ROLE_USER"),
                )
            }
    }

    class MutableJwtDecoder : JwtDecoder {
        private val acceptedTokens = mutableMapOf<String, Jwt>()
        private val rejectedTokens = mutableSetOf<String>()

        fun accept(
            token: String,
            username: String = "alice",
            roles: List<String>,
        ) {
            acceptedTokens[token] =
                Jwt
                    .withTokenValue(token)
                    .header("alg", "RS256")
                    .issuer("https://auth.jorisjonkers.dev")
                    .subject(UUID.randomUUID().toString())
                    .claim("username", username)
                    .claim("roles", roles)
                    .issuedAt(Instant.now().minusSeconds(60))
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build()
        }

        fun reject(token: String) {
            rejectedTokens += token
        }

        override fun decode(token: String): Jwt {
            if (token in rejectedTokens) throw BadJwtException("Rejected test token")
            return acceptedTokens[token] ?: throw BadJwtException("Unknown test token")
        }
    }

    data class PublicRequest(
        val builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder,
    )

    companion object {
        @JvmStatic
        fun rejectedBearerTokens(): List<String> = listOf("expired-token", "invalid-token", "unsigned-token")

        @JvmStatic
        fun publicEndpoints(): List<PublicRequest> =
            listOf(
                PublicRequest(post("/api/v1/users/register")),
                PublicRequest(post("/api/v1/auth/login")),
                PublicRequest(post("/api/v1/auth/refresh")),
                PublicRequest(post("/api/v1/auth/forgot-password")),
                PublicRequest(post("/api/v1/auth/reset-password")),
                PublicRequest(post("/api/v1/auth/resend-confirmation")),
                PublicRequest(get("/api/v1/auth/confirm-email")),
                PublicRequest(post("/api/v1/auth/session-login")),
                PublicRequest(post("/api/v1/auth/totp-challenge")),
                PublicRequest(get("/api/v1/health")),
                PublicRequest(get("/api/v1/api-docs/openapi.json")),
                PublicRequest(get("/api/v1/swagger-ui/index.html")),
            )
    }
}
