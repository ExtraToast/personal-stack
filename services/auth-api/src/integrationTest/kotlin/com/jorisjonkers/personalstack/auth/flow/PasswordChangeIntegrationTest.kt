package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.jooq.tables.AppUser.APP_USER
import com.jorisjonkers.personalstack.auth.jooq.tables.EmailConfirmationToken.EMAIL_CONFIRMATION_TOKEN
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class PasswordChangeIntegrationTest : IntegrationTestBase() {
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

    @Test
    fun `change password with correct current password succeeds`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "chgpwd_ok_$suffix"
        val oldPassword = "securepass123"
        val newPassword = "newSecurePass456"

        registerUser(username, oldPassword)
        confirmEmail(username)
        val session = sessionLogin(username, oldPassword)

        mockMvc
            .post("/api/v1/auth/change-password") {
                contentType = MediaType.APPLICATION_JSON
                this.session = session
                with(csrf())
                content = """{"currentPassword":"$oldPassword","newPassword":"$newPassword"}"""
            }.andExpect {
                status { isNoContent() }
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
    fun `change password with wrong current password returns 400`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "chgpwd_bad_$suffix"

        registerUser(username)
        confirmEmail(username)
        val session = sessionLogin(username)

        mockMvc
            .post("/api/v1/auth/change-password") {
                contentType = MediaType.APPLICATION_JSON
                this.session = session
                with(csrf())
                content = """{"currentPassword":"wrongpassword","newPassword":"newSecurePass456"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `change password returns 401 without session`() {
        mockMvc
            .post("/api/v1/auth/change-password") {
                contentType = MediaType.APPLICATION_JSON
                with(csrf())
                content = """{"currentPassword":"securepass123","newPassword":"newSecurePass456"}"""
            }.andExpect {
                status { isUnauthorized() }
            }
    }
}
