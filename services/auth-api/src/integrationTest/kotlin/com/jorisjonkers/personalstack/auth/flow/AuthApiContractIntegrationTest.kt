package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import com.jorisjonkers.personalstack.auth.jooq.tables.AppUser.APP_USER
import com.jorisjonkers.personalstack.auth.jooq.tables.EmailConfirmationToken.EMAIL_CONFIRMATION_TOKEN
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class AuthApiContractIntegrationTest : IntegrationTestBase() {
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

    @Test
    fun `OpenAPI spec endpoint returns valid JSON`() {
        mockMvc
            .get("/api/v1/api-docs") {
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.openapi") { exists() }
                jsonPath("$.info") { exists() }
                jsonPath("$.paths") { exists() }
            }
    }

    @Test
    fun `login endpoint response matches schema`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "contract_login_$suffix"
        val email = "contract_login_$suffix@example.com"
        val password = "securepass123"

        // Register user first
        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "$username",
                      "email": "$email",
                      "password": "$password"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        // Confirm email before login
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

        // Login returns a token response with totpRequired, accessToken, etc.
        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "$username",
                      "password": "$password"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.totpRequired") { exists() }
            }
    }

    @Test
    fun `register endpoint response matches schema`() {
        val suffix = UUID.randomUUID().toString().take(8)

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "contract_reg_$suffix",
                      "email": "contract_reg_$suffix@example.com",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.username") { value("contract_reg_$suffix") }
                jsonPath("$.email") { value("contract_reg_$suffix@example.com") }
                jsonPath("$.role") { exists() }
                jsonPath("$.totpEnabled") { exists() }
            }
    }

    @Test
    fun `admin users endpoint response matches schema`() {
        mockMvc
            .get("/api/v1/admin/users") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "admin-id",
                            roles = listOf("ROLE_ADMIN"),
                        ),
                    ),
                )
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$") { isArray() }
            }
    }

    @Test
    fun `health endpoint response matches schema`() {
        mockMvc
            .get("/api/v1/health") {
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.status") { value("ok") }
                jsonPath("$.service") { value("auth-api") }
            }
    }
}
