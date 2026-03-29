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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import java.util.UUID

class AuthRegistrationIntegrationTest : IntegrationTestBase() {
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

    @Test
    fun `register endpoint creates user in database`() {
        val requestBody =
            """
            {
              "username": "integrationuser",
              "email": "integration@example.com",
              "password": "securepass123"
            }
            """.trimIndent()

        mockMvc
            .post("/api/v1/users/register") {
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
        val firstBody =
            """
            {
              "username": "duplicate",
              "email": "first@example.com",
              "password": "securepass123"
            }
            """.trimIndent()

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content = firstBody
            }.andExpect { status { isCreated() } }

        val duplicateBody =
            """
            {
              "username": "duplicate",
              "email": "second@example.com",
              "password": "securepass123"
            }
            """.trimIndent()

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content = duplicateBody
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `oidc discovery endpoint is accessible`() {
        mockMvc
            .get("/.well-known/openid-configuration") {
            }.andExpect {
                status { isOk() }
                jsonPath("$.issuer") { exists() }
                jsonPath("$.token_endpoint") { exists() }
                jsonPath("$.authorization_endpoint") { exists() }
            }
    }

    @Test
    fun `register with duplicate email returns 400`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "dupemail_$suffix@example.com"

        val firstBody =
            """
            {
              "username": "reg_first_$suffix",
              "email": "$email",
              "password": "securepass123"
            }
            """.trimIndent()

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content = firstBody
            }.andExpect { status { isCreated() } }

        val duplicateBody =
            """
            {
              "username": "reg_second_$suffix",
              "email": "$email",
              "password": "securepass123"
            }
            """.trimIndent()

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content = duplicateBody
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `resend confirmation for already confirmed email fails`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "reg_confirmed_$suffix"
        val email = "$username@example.com"

        mockMvc
            .post("/api/v1/users/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "username": "$username",
                      "email": "$email",
                      "password": "securepass123"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        // Confirm the email
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

        // Resend should still return 200 (silent success for security)
        mockMvc
            .post("/api/v1/auth/resend-confirmation") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$email"}"""
            }.andExpect { status { isOk() } }
    }

    @Test
    fun `expired confirmation token returns 400`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "reg_expired_$suffix"

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

        // Expire the token
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
}
