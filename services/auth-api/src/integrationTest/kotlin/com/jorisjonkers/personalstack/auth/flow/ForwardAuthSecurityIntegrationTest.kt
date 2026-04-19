package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class ForwardAuthSecurityIntegrationTest : IntegrationTestBase() {
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

    @Test
    fun `verify endpoint without session returns redirect`() {
        mockMvc
            .get("/api/v1/auth/verify") {
            }.andExpect {
                status { is3xxRedirection() }
                header { string("Location", startsWith("http://localhost:5174/login?redirect=")) }
            }
    }

    @Test
    fun `verify endpoint with valid ADMIN session returns 200 with headers`() {
        val userId = UUID.randomUUID()
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(userId),
                            username = "admin-sec-1",
                            roles = listOf("ROLE_ADMIN"),
                        ),
                    ),
                )
            }.andExpect {
                status { isOk() }
                header { string("X-User-Id", userId.toString()) }
                header { string("X-User-Roles", "ROLE_ADMIN") }
            }
    }

    @Test
    fun `verify endpoint returns 403 for user without service permission`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "user-sec-1",
                            roles = listOf("ROLE_USER"),
                        ),
                    ),
                )
                header("X-Forwarded-Host", "vault.jorisjonkers.dev")
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `verify endpoint does not leak token in response`() {
        val result =
            mockMvc
                .get("/api/v1/auth/verify") {
                    with(
                        user(
                            AuthenticatedUser.of(
                                userId = UserId(UUID.randomUUID()),
                                username = "user-sec-2",
                                roles = listOf("ROLE_USER"),
                            ),
                        ),
                    )
                }.andExpect {
                    status { isOk() }
                }.andReturn()

        val responseBody = result.response.contentAsString
        val authHeader = result.response.getHeader("Authorization")

        assert(responseBody.isNullOrEmpty()) {
            "Expected empty response body from verify endpoint, but got: $responseBody"
        }
        assert(authHeader == null) {
            "Expected no Authorization header in response, but got: $authHeader"
        }
    }

    @Test
    fun `verify endpoint sets X-User-Id and X-User-Roles headers`() {
        val userId = UUID.randomUUID()
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(userId),
                            username = "user-sec-3",
                            roles = listOf("ROLE_USER", "SERVICE_GRAFANA"),
                        ),
                    ),
                )
                header("X-Forwarded-Host", "grafana.jorisjonkers.dev")
            }.andExpect {
                status { isOk() }
                header { string("X-User-Id", userId.toString()) }
                header { string("X-User-Roles", "ROLE_USER,SERVICE_GRAFANA") }
            }
    }
}
