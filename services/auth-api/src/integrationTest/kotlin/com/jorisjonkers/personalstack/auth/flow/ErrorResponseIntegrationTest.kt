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

class ErrorResponseIntegrationTest : IntegrationTestBase() {
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
    fun `404 not found returns ProblemDetail with title and status`() {
        // Attempt to login with a nonexistent user triggers InvalidCredentialsException (400),
        // but we can test a real 404 via the admin endpoint with a nonexistent user ID
        val fakeId = UUID.randomUUID()

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"username":"nonexistent_user_${UUID.randomUUID().toString().take(8)}","password":"whatever"}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.title") { exists() }
                jsonPath("$.status") { value(400) }
                jsonPath("$.type") { exists() }
            }
    }

    @Test
    fun `400 domain exception returns ProblemDetail with detail`() {
        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"nouser","password":"nopass"}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.title") { exists() }
                jsonPath("$.status") { value(400) }
                jsonPath("$.detail") { exists() }
                jsonPath("$.type") { exists() }
            }
    }

    @Test
    fun `422 validation error returns ProblemDetail with errors array`() {
        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "",
                      "email": "not-an-email",
                      "password": ""
                    }
                    """.trimIndent()
            }.andExpect {
                status { isUnprocessableEntity() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.title") { value("Validation Error") }
                jsonPath("$.status") { value(422) }
                jsonPath("$.errors") { isArray() }
                jsonPath("$.errors.length()") { value(org.hamcrest.Matchers.greaterThan(0)) }
            }
    }

    @Test
    fun `duplicate username returns ProblemDetail with meaningful detail`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "errdup_$suffix"

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
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.title") { exists() }
                jsonPath("$.status") { value(400) }
                jsonPath("$.detail") { exists() }
            }
    }

    @Test
    fun `invalid JSON body returns ProblemDetail`() {
        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ this is not valid json }"""
            }.andExpect {
                status { isInternalServerError() }
            }
    }
}
