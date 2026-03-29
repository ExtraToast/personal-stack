package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.jooq.tables.AppUser.APP_USER
import com.jorisjonkers.personalstack.auth.jooq.tables.EmailConfirmationToken.EMAIL_CONFIRMATION_TOKEN
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.Base64
import java.util.UUID

class TokenSecurityIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var dsl: DSLContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    private fun uniqueUsername() = "toksec_${UUID.randomUUID().toString().take(8)}"

    private fun registerAndConfirmUser(
        username: String,
        password: String,
    ) {
        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "$username",
                      "email": "$username@example.com",
                      "password": "$password"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        val userId =
            dsl
                .select(APP_USER.ID)
                .from(APP_USER)
                .where(APP_USER.USERNAME.eq(username))
                .fetchOne(APP_USER.ID)!!
        val token =
            dsl
                .select(EMAIL_CONFIRMATION_TOKEN.TOKEN)
                .from(EMAIL_CONFIRMATION_TOKEN)
                .where(EMAIL_CONFIRMATION_TOKEN.USER_ID.eq(userId))
                .fetchOne(EMAIL_CONFIRMATION_TOKEN.TOKEN)!!
        mockMvc
            .get("/api/v1/auth/confirm-email") {
                param("token", token)
            }.andExpect { status { isOk() } }
    }

    private fun loginAndGetTokens(
        username: String,
        password: String,
    ): Pair<String, String> {
        val result =
            mockMvc
                .post("/api/v1/auth/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"username":"$username","password":"$password"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

        val body = result.response.contentAsString
        val accessToken = Regex(""""accessToken"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
        val refreshToken = Regex(""""refreshToken"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
        return accessToken to refreshToken
    }

    @Test
    fun `expired access token is rejected on protected endpoint`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)
        val (accessToken, _) = loginAndGetTokens(username, password)

        // Tamper with the JWT to set an expired date by modifying the payload
        val parts = accessToken.split(".")
        val payloadJson = String(Base64.getUrlDecoder().decode(parts[1] + "==".take((4 - parts[1].length % 4) % 4)))
        // Replace the exp claim with a past timestamp (year 2020)
        val expiredPayload = payloadJson.replace(Regex(""""exp"\s*:\s*\d+"""), """"exp":1577836800""")
        val newPayload =
            Base64.getUrlEncoder().withoutPadding().encodeToString(expiredPayload.toByteArray())
        val tamperedToken = "${parts[0]}.$newPayload.${parts[2]}"

        mockMvc
            .get("/api/v1/admin/users") {
                header("Authorization", "Bearer $tamperedToken")
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `tampered JWT is rejected`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)
        val (accessToken, _) = loginAndGetTokens(username, password)

        // Modify the payload while keeping the original signature
        val parts = accessToken.split(".")
        val payloadJson = String(Base64.getUrlDecoder().decode(parts[1] + "==".take((4 - parts[1].length % 4) % 4)))
        val tamperedPayload = payloadJson.replace(""""username":"$username"""", """"username":"hacker"""")
        val newPayload =
            Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayload.toByteArray())
        val tamperedToken = "${parts[0]}.$newPayload.${parts[2]}"

        mockMvc
            .get("/api/v1/admin/users") {
                header("Authorization", "Bearer $tamperedToken")
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `token with wrong issuer is rejected`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)
        val (accessToken, _) = loginAndGetTokens(username, password)

        // Replace the issuer in the payload
        val parts = accessToken.split(".")
        val payloadJson = String(Base64.getUrlDecoder().decode(parts[1] + "==".take((4 - parts[1].length % 4) % 4)))
        val wrongIssuerPayload =
            payloadJson.replace(
                """"iss":"http://localhost"""",
                """"iss":"http://evil.example.com"""",
            )
        val newPayload =
            Base64.getUrlEncoder().withoutPadding().encodeToString(wrongIssuerPayload.toByteArray())
        val tamperedToken = "${parts[0]}.$newPayload.${parts[2]}"

        mockMvc
            .get("/api/v1/admin/users") {
                header("Authorization", "Bearer $tamperedToken")
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `refresh token cannot be used as access token`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)
        val (_, refreshToken) = loginAndGetTokens(username, password)

        // Try to use the refresh token as a Bearer token on a protected endpoint
        mockMvc
            .get("/api/v1/admin/users") {
                header("Authorization", "Bearer $refreshToken")
            }.andExpect {
                // Refresh tokens lack roles claim, so Spring Security won't grant ADMIN authority -> 403
                // Or the endpoint may reject it outright with 401 depending on filter chain processing
                status { is4xxClientError() }
            }
    }

    @Test
    fun `access token without required role returns 403`() {
        mockMvc
            .get("/api/v1/admin/users") {
                with(
                    jwt()
                        .jwt { it.subject("user-id").claim("roles", listOf("ROLE_USER")) }
                        .authorities(SimpleGrantedAuthority("ROLE_USER")),
                )
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `malformed JWT returns 401`() {
        mockMvc
            .get("/api/v1/admin/users") {
                header("Authorization", "Bearer this.is.not.a.jwt")
            }.andExpect {
                status { isUnauthorized() }
            }
    }
}
