package com.jorisjonkers.privatestack.auth.flow

import com.jorisjonkers.privatestack.auth.IntegrationTestBase
import com.jorisjonkers.privatestack.auth.domain.port.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
class AuthRegistrationIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `register endpoint creates user in database`() {
        val requestBody = """
            {
              "username": "integrationuser",
              "email": "integration@example.com",
              "password": "securepass123"
            }
        """.trimIndent()

        mockMvc.post("/api/v1/users/register") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isCreated() }
            jsonPath("$.username") { value("integrationuser") }
            jsonPath("$.email") { value("integration@example.com") }
            jsonPath("$.role") { value("USER") }
            jsonPath("$.totpEnabled") { value(false) }
        }

        assert(userRepository.existsByUsername("integrationuser"))
        assert(userRepository.existsByEmail("integration@example.com"))
    }

    @Test
    fun `register endpoint returns 400 for duplicate username`() {
        val firstBody = """
            {
              "username": "duplicate",
              "email": "first@example.com",
              "password": "securepass123"
            }
        """.trimIndent()

        mockMvc.post("/api/v1/users/register") {
            contentType = MediaType.APPLICATION_JSON
            content = firstBody
        }.andExpect { status { isCreated() } }

        val duplicateBody = """
            {
              "username": "duplicate",
              "email": "second@example.com",
              "password": "securepass123"
            }
        """.trimIndent()

        mockMvc.post("/api/v1/users/register") {
            contentType = MediaType.APPLICATION_JSON
            content = duplicateBody
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `oidc discovery endpoint is accessible`() {
        mockMvc.get("/.well-known/openid-configuration") {
        }.andExpect {
            status { isOk() }
            jsonPath("$.issuer") { value("https://auth.jorisjonkers.dev") }
            jsonPath("$.token_endpoint") { exists() }
            jsonPath("$.authorization_endpoint") { exists() }
        }
    }
}
