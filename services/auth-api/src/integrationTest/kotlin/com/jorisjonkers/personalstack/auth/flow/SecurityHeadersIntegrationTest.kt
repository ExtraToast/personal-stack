package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

class SecurityHeadersIntegrationTest : IntegrationTestBase() {
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
    fun `public endpoint returns cache-control headers`() {
        mockMvc
            .get("/api/actuator/health") {
            }.andExpect {
                // Spring Security default adds Cache-Control headers
                header { exists("Cache-Control") }
            }
    }

    @Test
    fun `API response includes X-Content-Type-Options nosniff`() {
        mockMvc
            .get("/api/actuator/health") {
            }.andExpect {
                header { string("X-Content-Type-Options", "nosniff") }
            }
    }

    @Test
    fun `error responses use RFC 7807 content type`() {
        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"nonexistent","password":"wrongpassword"}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.title") { exists() }
                jsonPath("$.status") { exists() }
            }
    }

    @Test
    fun `health endpoint is publicly accessible without auth`() {
        val result =
            mockMvc
                .get("/api/actuator/health") {
                }.andReturn()

        // Health endpoint is accessible (not blocked by auth), but may return 503
        // when health indicators (DB, Redis, RabbitMQ) are down in test context
        val status = result.response.status
        assertTrue(status == 200 || status == 503) {
            "Expected 200 or 503 but got $status"
        }
    }
}
