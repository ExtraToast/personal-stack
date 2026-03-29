package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class InputSanitizationIntegrationTest : IntegrationTestBase() {
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
    fun `registration with XSS in username is rejected by validation`() {
        val suffix = UUID.randomUUID().toString().take(8)

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "<script>alert('xss')</script>",
                      "email": "xss_$suffix@example.com",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect {
                // Username pattern validation rejects special characters
                status { isUnprocessableEntity() }
            }
    }

    @Test
    fun `registration with SQL injection in username is rejected by validation`() {
        val suffix = UUID.randomUUID().toString().take(8)

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "'; DROP TABLE app_user; --",
                      "email": "sqli_$suffix@example.com",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect {
                // Username pattern validation rejects special characters
                status { isUnprocessableEntity() }
            }
    }

    @Test
    fun `login with extremely long password is handled gracefully`() {
        val longPassword = "a".repeat(10000)

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "anyuser",
                      "password": "$longPassword"
                    }
                    """.trimIndent()
            }.andExpect {
                // Should respond without crashing - either 400 (invalid credentials) or 422 (validation)
                status { is4xxClientError() }
            }
    }
}
