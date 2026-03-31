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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class ProfileUpdateIntegrationTest : IntegrationTestBase() {
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
    fun `PATCH profile updates firstName and lastName`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "profile_upd_$suffix"

        registerUser(username)
        confirmEmail(username)
        val session = sessionLogin(username)

        mockMvc
            .patch("/api/v1/users/me") {
                contentType = MediaType.APPLICATION_JSON
                this.session = session
                with(csrf())
                content = """{"firstName":"Updated","lastName":"Name"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.firstName") { value("Updated") }
                jsonPath("$.lastName") { value("Name") }
            }

        mockMvc
            .get("/api/v1/auth/me") {
                this.session = session
            }.andExpect {
                status { isOk() }
                jsonPath("$.firstName") { value("Updated") }
                jsonPath("$.lastName") { value("Name") }
            }
    }

    @Test
    fun `PATCH profile returns 401 without session`() {
        mockMvc
            .patch("/api/v1/users/me") {
                contentType = MediaType.APPLICATION_JSON
                with(csrf())
                content = """{"firstName":"Updated","lastName":"Name"}"""
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `PATCH profile returns 422 with blank firstName`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "profile_blank_$suffix"

        registerUser(username)
        confirmEmail(username)
        val session = sessionLogin(username)

        mockMvc
            .patch("/api/v1/users/me") {
                contentType = MediaType.APPLICATION_JSON
                this.session = session
                with(csrf())
                content = """{"firstName":"","lastName":"Name"}"""
            }.andExpect {
                status { isUnprocessableContent() }
            }
    }
}
