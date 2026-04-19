package com.jorisjonkers.personalstack.auth.flow

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.jooq.tables.AppUser.APP_USER
import com.jorisjonkers.personalstack.auth.jooq.tables.EmailConfirmationToken.EMAIL_CONFIRMATION_TOKEN
import org.jooq.DSLContext
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
import java.time.LocalDateTime
import java.util.UUID

class EmailConfirmationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var dsl: DSLContext

    private lateinit var mockMvc: MockMvc

    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    private fun uniqueUsername() = "conf_${UUID.randomUUID().toString().take(8)}"

    private fun registerUser(username: String) {
        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"username":"$username","email":"$username@test.com","firstName":"Test","lastName":"User","password":"Secure123!"}"""
            }.andExpect { status { isCreated() } }
    }

    private fun getConfirmationToken(username: String): String {
        val userId =
            dsl
                .select(APP_USER.ID)
                .from(APP_USER)
                .where(APP_USER.USERNAME.eq(username))
                .fetchOne(APP_USER.ID)!!
        return dsl
            .select(EMAIL_CONFIRMATION_TOKEN.TOKEN)
            .from(EMAIL_CONFIRMATION_TOKEN)
            .where(EMAIL_CONFIRMATION_TOKEN.USER_ID.eq(userId))
            .fetchOne(EMAIL_CONFIRMATION_TOKEN.TOKEN)!!
    }

    @Test
    fun `login is rejected when email is not confirmed`() {
        val username = uniqueUsername()
        registerUser(username)

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"$username","password":"Secure123!"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("Email address has not been confirmed") }
            }
    }

    @Test
    fun `confirm email with valid token enables login`() {
        val username = uniqueUsername()
        registerUser(username)

        val token = getConfirmationToken(username)

        mockMvc
            .get("/api/v1/auth/confirm-email") {
                param("token", token)
            }.andExpect { status { isOk() } }

        val result =
            mockMvc
                .post("/api/v1/auth/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"username":"$username","password":"Secure123!"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        assert(!json["totpRequired"].asBoolean())
        assert(json["accessToken"] != null && !json["accessToken"].isNull)
    }

    @Test
    fun `confirm email with invalid token returns 400`() {
        mockMvc
            .get("/api/v1/auth/confirm-email") {
                param("token", UUID.randomUUID().toString())
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `confirm email with already-used token returns 400`() {
        val username = uniqueUsername()
        registerUser(username)
        val token = getConfirmationToken(username)

        mockMvc
            .get("/api/v1/auth/confirm-email") {
                param("token", token)
            }.andExpect { status { isOk() } }

        mockMvc
            .get("/api/v1/auth/confirm-email") {
                param("token", token)
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `confirm email with expired token returns 400`() {
        val username = uniqueUsername()
        registerUser(username)
        val token = getConfirmationToken(username)

        val userId =
            dsl
                .select(APP_USER.ID)
                .from(APP_USER)
                .where(APP_USER.USERNAME.eq(username))
                .fetchOne(APP_USER.ID)!!
        dsl
            .update(EMAIL_CONFIRMATION_TOKEN)
            .set(EMAIL_CONFIRMATION_TOKEN.EXPIRES_AT, LocalDateTime.of(2020, 1, 1, 0, 0))
            .where(EMAIL_CONFIRMATION_TOKEN.USER_ID.eq(userId))
            .execute()

        mockMvc
            .get("/api/v1/auth/confirm-email") {
                param("token", token)
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `resend confirmation returns 200 even for unknown email`() {
        mockMvc
            .post("/api/v1/auth/resend-confirmation") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"nonexistent@test.com"}"""
            }.andExpect { status { isOk() } }
    }

    @Test
    fun `resend confirmation creates new token`() {
        val username = uniqueUsername()
        registerUser(username)

        val firstToken = getConfirmationToken(username)

        mockMvc
            .post("/api/v1/auth/resend-confirmation") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$username@test.com"}"""
            }.andExpect { status { isOk() } }

        val userId =
            dsl
                .select(APP_USER.ID)
                .from(APP_USER)
                .where(APP_USER.USERNAME.eq(username))
                .fetchOne(APP_USER.ID)!!
        val tokens =
            dsl
                .select(EMAIL_CONFIRMATION_TOKEN.TOKEN)
                .from(EMAIL_CONFIRMATION_TOKEN)
                .where(EMAIL_CONFIRMATION_TOKEN.USER_ID.eq(userId))
                .fetch(EMAIL_CONFIRMATION_TOKEN.TOKEN)

        assert(tokens.isNotEmpty())
        val newToken = tokens.last()
        assert(newToken != firstToken) { "Expected a new token to be created" }
    }
}
