package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import com.jorisjonkers.personalstack.auth.jooq.tables.AppUser.APP_USER
import com.jorisjonkers.personalstack.auth.jooq.tables.EmailConfirmationToken.EMAIL_CONFIRMATION_TOKEN
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
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

    private fun sessionLoginAndGetSession(
        username: String,
        password: String,
    ): MockHttpSession {
        val result =
            mockMvc
                .post("/api/v1/auth/session-login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"username":"$username","password":"$password"}"""
                }.andExpect { status { isOk() } }
                .andReturn()
        return result.request.getSession(false) as MockHttpSession
    }

    @Test
    fun `Bearer token without session is rejected on protected endpoint`() {
        mockMvc
            .get("/api/v1/admin/users") {
                header("Authorization", "Bearer some-token-value")
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `session user without required role returns 403`() {
        mockMvc
            .get("/api/v1/admin/users") {
                with(
                    user(
                        AuthenticatedUser(
                            userId = UserId(UUID.randomUUID()),
                            username = "user-id",
                            roles = listOf("ROLE_USER"),
                        ),
                    ),
                )
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `no authentication returns 401`() {
        mockMvc
            .get("/api/v1/admin/users")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `session login creates usable session for protected endpoints`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)

        val session = sessionLoginAndGetSession(username, password)

        // Use the session to access a protected endpoint
        mockMvc
            .get("/api/v1/auth/me") {
                this.session = session
            }.andExpect {
                status { isOk() }
                jsonPath("$.username") { value(username) }
            }
    }

    @Test
    fun `invalidated session is rejected`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)

        val session = sessionLoginAndGetSession(username, password)
        session.invalidate()

        mockMvc
            .get("/api/v1/auth/me") {
                this.session = session
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `session with ADMIN role can access admin endpoints`() {
        val adminUser =
            AuthenticatedUser(
                userId = UserId(UUID.randomUUID()),
                username = "admin-session",
                roles = listOf("ROLE_ADMIN"),
            )
        val session = MockHttpSession()
        val auth = UsernamePasswordAuthenticationToken(adminUser, null, adminUser.authorities)
        val ctx = SecurityContextHolder.createEmptyContext()
        ctx.authentication = auth
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx)

        mockMvc
            .get("/api/v1/admin/users") {
                this.session = session
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `different sessions are isolated`() {
        val user1 = uniqueUsername()
        val user2 = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(user1, password)
        registerAndConfirmUser(user2, password)

        val session1 = sessionLoginAndGetSession(user1, password)
        val session2 = sessionLoginAndGetSession(user2, password)

        assertThat(session1.id).isNotEqualTo(session2.id)

        val result1 =
            mockMvc
                .get("/api/v1/auth/me") {
                    session = session1
                }.andExpect { status { isOk() } }
                .andReturn()

        val result2 =
            mockMvc
                .get("/api/v1/auth/me") {
                    session = session2
                }.andExpect { status { isOk() } }
                .andReturn()

        assertThat(result1.response.contentAsString).contains(user1)
        assertThat(result2.response.contentAsString).contains(user2)
    }
}
