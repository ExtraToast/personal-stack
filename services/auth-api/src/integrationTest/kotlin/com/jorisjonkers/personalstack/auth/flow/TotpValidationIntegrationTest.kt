package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

class TotpValidationIntegrationTest : IntegrationTestBase() {
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
    fun `TOTP verify with non-6-digit code returns 422`() {
        mockMvc
            .post("/api/v1/totp/verify") {
                with(jwt().jwt { it.subject("user-id") })
                contentType = MediaType.APPLICATION_JSON
                content = """{"code":"123"}"""
            }.andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.title") { value("Validation Error") }
                jsonPath("$.errors[0].field") { value("code") }
            }
    }

    @Test
    fun `TOTP verify with non-numeric code returns 422`() {
        mockMvc
            .post("/api/v1/totp/verify") {
                with(jwt().jwt { it.subject("user-id") })
                contentType = MediaType.APPLICATION_JSON
                content = """{"code":"abcdef"}"""
            }.andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.errors[0].field") { value("code") }
            }
    }

    @Test
    fun `TOTP challenge with blank token returns 422`() {
        mockMvc
            .post("/api/v1/auth/totp-challenge") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"totpChallengeToken":"","code":"123456"}"""
            }.andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.errors") { isNotEmpty() }
            }
    }
}
