package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class SecurityFilterChainRoutingIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    private fun assertNotUnauthorized(
        path: String,
        status: Int,
    ) {
        assertThat(status)
            .describedAs("$path should be accessible without auth (not 401)")
            .isNotEqualTo(401)
    }

    @Nested
    inner class HealthEndpoints {
        @Test
        fun `GET actuator health is accessible without auth`() {
            val result = mockMvc.get("/api/actuator/health").andReturn()
            // 200 (healthy) or 503 (unhealthy, e.g. mail health check fails in tests)
            // Both confirm the endpoint is publicly accessible (not 401/403)
            assertThat(result.response.status)
                .describedAs("Health endpoint should be accessible without auth")
                .isIn(200, 503)
        }

        @Test
        fun `GET actuator health liveness is accessible without auth`() {
            mockMvc
                .get("/api/actuator/health/liveness")
                .andExpect { status { isOk() } }
        }

        @Test
        fun `GET actuator info is accessible without auth`() {
            mockMvc
                .get("/api/actuator/info")
                .andExpect { status { isOk() } }
        }

        @Test
        fun `GET v1 health is accessible without auth`() {
            mockMvc
                .get("/api/v1/health")
                .andExpect { status { isOk() } }
        }
    }

    @Nested
    inner class PublicApiEndpoints {
        @Test
        fun `POST register is accessible without auth`() {
            val result =
                mockMvc
                    .post("/api/v1/users/register") {
                        contentType = MediaType.APPLICATION_JSON
                        content = "{}"
                    }.andReturn()
            assertNotUnauthorized("/api/v1/users/register", result.response.status)
        }

        @Test
        fun `POST login is accessible without auth`() {
            val result =
                mockMvc
                    .post("/api/v1/auth/login") {
                        contentType = MediaType.APPLICATION_JSON
                        content = "{}"
                    }.andReturn()
            assertNotUnauthorized("/api/v1/auth/login", result.response.status)
        }

        @Test
        fun `POST totp-challenge is accessible without auth`() {
            val result =
                mockMvc
                    .post("/api/v1/auth/totp-challenge") {
                        contentType = MediaType.APPLICATION_JSON
                        content = "{}"
                    }.andReturn()
            assertNotUnauthorized("/api/v1/auth/totp-challenge", result.response.status)
        }

        @Test
        fun `POST refresh is accessible without auth`() {
            val result =
                mockMvc
                    .post("/api/v1/auth/refresh") {
                        contentType = MediaType.APPLICATION_JSON
                        content = "{}"
                    }.andReturn()
            assertNotUnauthorized("/api/v1/auth/refresh", result.response.status)
        }

        @Test
        fun `GET confirm-email is accessible without auth`() {
            val result =
                mockMvc
                    .get("/api/v1/auth/confirm-email") {
                        param("token", "invalid-token")
                    }.andReturn()
            assertNotUnauthorized("/api/v1/auth/confirm-email", result.response.status)
        }

        @Test
        fun `POST resend-confirmation is accessible without auth`() {
            val result =
                mockMvc
                    .post("/api/v1/auth/resend-confirmation") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"email":"test@example.com"}"""
                    }.andReturn()
            assertNotUnauthorized("/api/v1/auth/resend-confirmation", result.response.status)
        }

        @Test
        fun `POST session-login is accessible without auth`() {
            val result =
                mockMvc
                    .post("/api/v1/auth/session-login") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"username":"nonexistent","password":"test"}"""
                    }.andReturn()
            assertNotUnauthorized("/api/v1/auth/session-login", result.response.status)
        }
    }

    @Nested
    inner class ProtectedEndpoints {
        @Test
        fun `GET admin users without auth returns 401`() {
            mockMvc
                .get("/api/v1/admin/users")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `POST totp enroll without auth is rejected`() {
            val result = mockMvc.post("/api/v1/totp/enroll").andReturn()
            // 401 (no session) or 403 (CSRF rejection before auth check)
            assertThat(result.response.status).isIn(401, 403)
        }

        @Test
        fun `GET admin users with USER role returns 403`() {
            mockMvc
                .get("/api/v1/admin/users") {
                    with(
                        user(
                            AuthenticatedUser.of(
                                userId = UserId(UUID.randomUUID()),
                                username = "user-1",
                                roles = listOf("ROLE_USER"),
                            ),
                        ),
                    )
                }.andExpect {
                    status { isForbidden() }
                }
        }

        @Test
        fun `GET admin users with ADMIN role returns 200`() {
            mockMvc
                .get("/api/v1/admin/users") {
                    with(
                        user(
                            AuthenticatedUser.of(
                                userId = UserId(UUID.randomUUID()),
                                username = "admin-1",
                                roles = listOf("ROLE_ADMIN"),
                            ),
                        ),
                    )
                }.andExpect {
                    status { isOk() }
                }
        }
    }

    @Nested
    inner class ForwardAuthChain {
        @Test
        fun `GET verify without auth returns 302 redirect to login`() {
            mockMvc
                .get("/api/v1/auth/verify")
                .andExpect {
                    status { is3xxRedirection() }
                    header {
                        string("Location", org.hamcrest.Matchers.containsString("/login"))
                    }
                }
        }

        @Test
        fun `GET verify without auth does not redirect to session-login`() {
            val result =
                mockMvc
                    .get("/api/v1/auth/verify")
                    .andReturn()
            val location = result.response.getHeader("Location")
            assertThat(location).doesNotContain("session-login")
        }

        @Test
        fun `GET verify with valid session returns 200 with user headers`() {
            val userId = UUID.randomUUID()
            mockMvc
                .get("/api/v1/auth/verify") {
                    with(
                        user(
                            AuthenticatedUser.of(
                                userId = UserId(userId),
                                username = "user-fwd-1",
                                roles = listOf("ROLE_USER"),
                            ),
                        ),
                    )
                }.andExpect {
                    status { isOk() }
                    header { string("X-User-Id", userId.toString()) }
                    header { string("X-User-Roles", "ROLE_USER") }
                }
        }
    }

    @Nested
    inner class OAuth2Endpoints {
        @Test
        fun `GET authorize without session returns redirect not to session-login`() {
            val result =
                mockMvc
                    .get("/api/oauth2/authorize") {
                        param("response_type", "code")
                        param("client_id", "auth-ui")
                        param("redirect_uri", "http://localhost:5174/callback")
                        param("scope", "openid")
                        param("code_challenge", "test-challenge")
                        param("code_challenge_method", "S256")
                        accept = MediaType.TEXT_HTML
                    }.andReturn()

            val status = result.response.status
            val location = result.response.getHeader("Location")
            assertThat(status)
                .describedAs("Expected 302, 400, or 401 but got $status")
                .isIn(302, 400, 401)
            if (location != null) {
                assertThat(location)
                    .describedAs("OAuth2 authorize should NOT redirect to session-login")
                    .doesNotContain("session-login")
            }
        }

        @Test
        fun `POST token with invalid grant returns 400 or 302`() {
            val result =
                mockMvc
                    .post("/api/oauth2/token") {
                        contentType = MediaType.APPLICATION_FORM_URLENCODED
                        content = "grant_type=authorization_code&code=invalid&client_id=auth-ui"
                    }.andReturn()
            // 400 (invalid grant) or 302 (auth server redirect for unauthenticated client)
            assertThat(result.response.status)
                .describedAs("Token endpoint should return 400 or 302, not redirect to session-login")
                .isIn(400, 302)
            val location = result.response.getHeader("Location")
            if (location != null) {
                assertThat(location).doesNotContain("session-login")
            }
        }

        @Test
        fun `OIDC discovery is accessible without auth`() {
            mockMvc
                .get("/.well-known/openid-configuration")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.issuer") { exists() }
                    jsonPath("$.authorization_endpoint") { exists() }
                    jsonPath("$.token_endpoint") { exists() }
                    jsonPath("$.jwks_uri") { exists() }
                }
        }

        @Test
        fun `JWKS endpoint is accessible without auth`() {
            mockMvc
                .get("/api/oauth2/jwks")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.keys") { exists() }
                }
        }
    }

    @Nested
    inner class CrossChainIsolation {
        @Test
        fun `unknown api path without auth returns 401 not redirect`() {
            mockMvc
                .get("/api/v1/nonexistent")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `GET on login endpoint does not redirect to session-login`() {
            val result =
                mockMvc
                    .get("/api/v1/auth/login")
                    .andReturn()

            val status = result.response.status
            val location = result.response.getHeader("Location")
            // 401 (no auth), 405 (wrong method), or 500 (no body for POST-only endpoint)
            // The key assertion: it does NOT redirect to session-login
            assertThat(status)
                .describedAs("GET /api/v1/auth/login should not return 200 or 302 to session-login")
                .isNotEqualTo(200)
            if (location != null) {
                assertThat(location).doesNotContain("session-login")
            }
        }

        @Test
        fun `no protected endpoint redirects to session-login`() {
            val paths =
                listOf(
                    "/api/v1/admin/users",
                    "/api/v1/totp/enroll",
                    "/api/v1/nonexistent",
                )
            for (path in paths) {
                val result = mockMvc.get(path).andReturn()
                val location = result.response.getHeader("Location")
                assertThat(location)
                    .describedAs("$path should NOT redirect to session-login")
                    .satisfies({
                        if (it != null) {
                            assertThat(it).doesNotContain("session-login")
                        }
                    })
            }
        }
    }
}
