package com.jorisjonkers.privatestack.auth.flow

import com.jorisjonkers.privatestack.auth.IntegrationTestBase
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

class ForwardAuthIntegrationTest : IntegrationTestBase() {
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
    fun `verify endpoint returns 200 with user identity headers for valid JWT`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    jwt().jwt { jwt ->
                        jwt.subject("user-123")
                        jwt.claim("roles", listOf("USER"))
                    },
                )
            }.andExpect {
                status { isOk() }
                header { string("X-User-Id", "user-123") }
                header { string("X-User-Roles", "USER") }
            }
    }

    @Test
    fun `verify endpoint redirects to login without authentication`() {
        mockMvc
            .get("/api/v1/auth/verify") {
            }.andExpect {
                status { is3xxRedirection() }
                header { string("Location", startsWith("http://localhost:5174/login?redirect=")) }
            }
    }

    @Test
    fun `verify endpoint preserves original URL from forwarded headers in redirect`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                header("X-Forwarded-Proto", "https")
                header("X-Forwarded-Host", "grafana.jorisjonkers.dev")
                header("X-Forwarded-Uri", "/d/dashboard")
            }.andExpect {
                status { is3xxRedirection() }
                header {
                    string(
                        "Location",
                        "http://localhost:5174/login?redirect=https%3A%2F%2Fgrafana.jorisjonkers.dev%2Fd%2Fdashboard",
                    )
                }
            }
    }
}
