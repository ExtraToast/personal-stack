package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

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
    fun `verify endpoint without token returns redirect`() {
        mockMvc
            .get("/api/v1/auth/verify") {
            }.andExpect {
                status { is3xxRedirection() }
                header { string("Location", startsWith("http://localhost:5174/login?redirect=")) }
            }
    }

    @Test
    fun `verify endpoint with valid ADMIN token returns 200 with headers`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    jwt()
                        .jwt { jwt ->
                            jwt.subject("admin-sec-1")
                            jwt.claim("roles", listOf("ROLE_ADMIN"))
                        }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
                )
            }.andExpect {
                status { isOk() }
                header { string("X-User-Id", "admin-sec-1") }
                header { string("X-User-Roles", "ROLE_ADMIN") }
            }
    }

    @Test
    fun `verify endpoint returns 403 for user without service permission`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    jwt()
                        .jwt { jwt ->
                            jwt.subject("user-sec-1")
                            jwt.claim("roles", listOf("ROLE_USER"))
                        }.authorities(SimpleGrantedAuthority("ROLE_USER")),
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
                        jwt()
                            .jwt { jwt ->
                                jwt.subject("user-sec-2")
                                jwt.claim("roles", listOf("ROLE_USER"))
                            }.authorities(SimpleGrantedAuthority("ROLE_USER")),
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
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    jwt()
                        .jwt { jwt ->
                            jwt.subject("user-sec-3")
                            jwt.claim("roles", listOf("ROLE_USER", "SERVICE_GRAFANA"))
                        }.authorities(
                            SimpleGrantedAuthority("ROLE_USER"),
                            SimpleGrantedAuthority("SERVICE_GRAFANA"),
                        ),
                )
                header("X-Forwarded-Host", "grafana.jorisjonkers.dev")
            }.andExpect {
                status { isOk() }
                header { string("X-User-Id", "user-sec-3") }
                header { string("X-User-Roles", "ROLE_USER,SERVICE_GRAFANA") }
            }
    }
}
