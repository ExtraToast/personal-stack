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

class SessionLoginValidationIntegrationTest : IntegrationTestBase() {
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
    fun `session login with blank username returns 422`() {
        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "",
                      "password": "somepassword"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.title") { value("Validation Error") }
                jsonPath("$.errors") { isNotEmpty() }
            }
    }

    @Test
    fun `session login with blank password returns 422`() {
        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "someuser",
                      "password": ""
                    }
                    """.trimIndent()
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.errors") { isNotEmpty() }
            }
    }

    @Test
    fun `session login with nonexistent user returns 400`() {
        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "nonexistent_${UUID.randomUUID().toString().take(8)}",
                      "password": "somepassword"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `session login with wrong password returns 400`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "sessval_$suffix"

        // Register user first
        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "$username",
                      "email": "$username@example.com",
                      "password": "correctpass123"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        // Attempt session login with wrong password
        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "$username",
                      "password": "wrongpassword"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
            }
    }
}
