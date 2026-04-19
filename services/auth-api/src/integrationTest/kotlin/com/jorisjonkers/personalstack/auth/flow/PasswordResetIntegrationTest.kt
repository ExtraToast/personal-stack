package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.jooq.tables.AppUser.APP_USER
import com.jorisjonkers.personalstack.auth.jooq.tables.EmailConfirmationToken.EMAIL_CONFIRMATION_TOKEN
import com.jorisjonkers.personalstack.auth.jooq.tables.PasswordResetToken.PASSWORD_RESET_TOKEN
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import java.util.UUID

class PasswordResetIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var userRepository: UserRepository

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

    private fun registerUser(
        username: String,
        password: String = "securepass123",
    ) {
        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"username":"$username","email":"$username@example.com","firstName":"Test","lastName":"User","password":"$password"}"""
            }.andExpect { status { isCreated() } }
    }

    private fun confirmEmail(username: String) {
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

    private fun sessionLogin(
        username: String,
        password: String = "securepass123",
    ): MockHttpSession {
        val result =
            mockMvc
                .post("/api/v1/auth/session-login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"username":"$username","password":"$password"}"""
                }.andReturn()
        return result.request.getSession(false) as MockHttpSession
    }

    private fun getUserId(username: String): UUID =
        dsl
            .select(APP_USER.ID)
            .from(APP_USER)
            .where(APP_USER.USERNAME.eq(username))
            .fetchOne(APP_USER.ID)!!

    private fun getResetToken(userId: UUID): String =
        dsl
            .select(PASSWORD_RESET_TOKEN.TOKEN)
            .from(PASSWORD_RESET_TOKEN)
            .where(PASSWORD_RESET_TOKEN.USER_ID.eq(userId))
            .fetchOne(PASSWORD_RESET_TOKEN.TOKEN)!!

    @Test
    fun `forgot-password returns 200 for existing email`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "forgot_ok_$suffix"

        registerUser(username)
        confirmEmail(username)

        mockMvc
            .post("/api/v1/auth/forgot-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$username@example.com"}"""
            }.andExpect {
                status { isOk() }
            }

        val userId = getUserId(username)
        val tokenCount =
            dsl
                .selectCount()
                .from(PASSWORD_RESET_TOKEN)
                .where(PASSWORD_RESET_TOKEN.USER_ID.eq(userId))
                .fetchOne(0, Int::class.java)!!
        assertThat(tokenCount).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `forgot-password returns 200 for non-existent email`() {
        val suffix = UUID.randomUUID().toString().take(8)

        mockMvc
            .post("/api/v1/auth/forgot-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"nonexistent_$suffix@example.com"}"""
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `reset-password with valid token changes password`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "reset_ok_$suffix"
        val newPassword = "newpass12345"

        registerUser(username)
        confirmEmail(username)

        mockMvc
            .post("/api/v1/auth/forgot-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$username@example.com"}"""
            }.andExpect { status { isOk() } }

        val userId = getUserId(username)
        val token = getResetToken(userId)

        mockMvc
            .post("/api/v1/auth/reset-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"$token","newPassword":"$newPassword"}"""
            }.andExpect {
                status { isOk() }
            }

        // Verify login with new password works
        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"$username","password":"$newPassword"}"""
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `reset-password with expired token returns 400`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "reset_exp_$suffix"

        registerUser(username)
        confirmEmail(username)

        mockMvc
            .post("/api/v1/auth/forgot-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$username@example.com"}"""
            }.andExpect { status { isOk() } }

        val userId = getUserId(username)
        val token = getResetToken(userId)

        // Expire the token
        dsl
            .update(PASSWORD_RESET_TOKEN)
            .set(PASSWORD_RESET_TOKEN.EXPIRES_AT, LocalDateTime.of(2020, 1, 1, 0, 0))
            .where(PASSWORD_RESET_TOKEN.USER_ID.eq(userId))
            .execute()

        mockMvc
            .post("/api/v1/auth/reset-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"$token","newPassword":"newSecurePass456"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `reset-password with already used token returns 400`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "reset_used_$suffix"
        val newPassword = "newpass12345"

        registerUser(username)
        confirmEmail(username)

        mockMvc
            .post("/api/v1/auth/forgot-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$username@example.com"}"""
            }.andExpect { status { isOk() } }

        val userId = getUserId(username)
        val token = getResetToken(userId)

        // Use the token once
        mockMvc
            .post("/api/v1/auth/reset-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"$token","newPassword":"$newPassword"}"""
            }.andExpect {
                status { isOk() }
            }

        // Attempt to use the same token again
        mockMvc
            .post("/api/v1/auth/reset-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"$token","newPassword":"anotherPass999"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }
}
