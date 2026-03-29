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

class RegisterValidationIntegrationTest : IntegrationTestBase() {
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
    fun `register with blank username returns 422 with field error`() {
        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "",
                      "email": "valid@example.com",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.title") { value("Validation Error") }
                jsonPath("$.errors[0].field") { value("username") }
            }
    }

    @Test
    fun `register with blank email returns 422`() {
        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "validuser",
                      "email": "",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.errors") { isNotEmpty() }
            }
    }

    @Test
    fun `register with blank password returns 422`() {
        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "validuser2",
                      "email": "valid2@example.com",
                      "password": ""
                    }
                    """.trimIndent()
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.errors") { isNotEmpty() }
            }
    }

    @Test
    fun `register with invalid email format returns 422`() {
        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "validuser3",
                      "email": "not-an-email",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.errors[0].field") { value("email") }
            }
    }

    @Test
    fun `register with very long username returns 422`() {
        val longUsername = "a".repeat(256)

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "$longUsername",
                      "email": "long@example.com",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.errors") { isNotEmpty() }
            }
    }

    @Test
    fun `register with duplicate username returns 400 with ProblemDetail`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "dupeval_$suffix"

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "$username",
                      "email": "$username@example.com",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "$username",
                      "email": "${username}_2@example.com",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.title") { exists() }
                jsonPath("$.detail") { exists() }
            }
    }
}
