package com.jorisjonkers.personalstack.auth.flow

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.auth.jooq.tables.AppUser.APP_USER
import com.jorisjonkers.personalstack.auth.jooq.tables.EmailConfirmationToken.EMAIL_CONFIRMATION_TOKEN
import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.apache.commons.codec.binary.Base32
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class SessionManagementIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var dsl: DSLContext

    @Autowired
    private lateinit var totpService: TotpService

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

    private fun uniqueUsername() = "sess_${UUID.randomUUID().toString().take(8)}"

    private fun registerUser(
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

    private fun registerAndConfirmUser(
        username: String,
        password: String,
    ): String {
        registerUser(username, password)
        confirmEmail(username)
        return username
    }

    private fun sessionLogin(
        username: String,
        password: String,
        totpCode: String? = null,
    ): MvcResult {
        val request =
            if (totpCode != null) {
                """{"username":"$username","password":"$password","totpCode":"$totpCode"}"""
            } else {
                """{"username":"$username","password":"$password"}"""
            }
        return mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = request
            }.andReturn()
    }

    private fun extractSession(result: MvcResult): MockHttpSession? =
        result.request.getSession(false) as? MockHttpSession

    private fun generateTotpCode(secret: String): String {
        val padded = secret.padEnd((secret.length + 7) / 8 * 8, '=')
        val secretBytes = Base32().decode(padded)
        val config =
            TimeBasedOneTimePasswordConfig(
                codeDigits = 6,
                hmacAlgorithm = HmacAlgorithm.SHA1,
                timeStep = 30,
                timeStepUnit = TimeUnit.SECONDS,
            )
        return TimeBasedOneTimePasswordGenerator(secretBytes, config).generate()
    }

    private fun enrollAndEnableTotp(
        username: String,
        password: String,
    ): String {
        val loginResult =
            mockMvc
                .post("/api/v1/auth/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"username":"$username","password":"$password"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

        val loginJson = objectMapper.readTree(loginResult.response.contentAsString)
        val accessToken = loginJson["accessToken"].asText()

        val enrollResult =
            mockMvc
                .post("/api/v1/totp/enroll") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect { status { isOk() } }
                .andReturn()

        val enrollJson = objectMapper.readTree(enrollResult.response.contentAsString)
        val secret = enrollJson["secret"].asText()

        val code = generateTotpCode(secret)
        mockMvc
            .post("/api/v1/totp/verify") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $accessToken")
                content = """{"code":"$code"}"""
            }.andExpect { status { isNoContent() } }

        return secret
    }

    @Test
    fun `session login stores SecurityContext in session`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)

        val result = sessionLogin(username, password)

        assert(result.response.status == 200) { "Expected 200 but got ${result.response.status}" }
        val session = extractSession(result)
        assert(session != null) { "Expected HttpSession to be created" }
    }

    @Test
    fun `session persists across multiple requests`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)

        val loginResult = sessionLogin(username, password)
        val session = extractSession(loginResult)!!

        // Second request with the session should also succeed
        val secondResult =
            mockMvc
                .get("/oauth2/authorize") {
                    param("response_type", "code")
                    param("client_id", "auth-ui")
                    param("redirect_uri", "http://localhost:5174/callback")
                    param("scope", "openid")
                    param("code_challenge", "test-challenge")
                    param("code_challenge_method", "S256")
                    this.session = session
                }.andReturn()

        // The authorize endpoint should recognize the session (not redirect to login)
        val location = secondResult.response.getHeader("Location")
        if (secondResult.response.status in 300..399 && location != null) {
            assert(!location.contains("/login")) {
                "Expected session to persist, but got redirect to login: $location"
            }
        }
    }

    @Test
    fun `session cookie is set in response`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)

        val result = sessionLogin(username, password)
        val session = extractSession(result)

        assert(session != null) { "Expected HttpSession to be created" }
        assert(!session!!.isInvalid) { "Expected session to be valid" }
    }

    @Test
    fun `CORS allows credentials for session-login`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)

        val result =
            mockMvc
                .post("/api/v1/auth/session-login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"username":"$username","password":"$password"}"""
                    header("Origin", "http://localhost:5174")
                }.andExpect {
                    status { isOk() }
                }.andReturn()

        val allowCredentials = result.response.getHeader("Access-Control-Allow-Credentials")
        assert(allowCredentials == "true") {
            "Expected Access-Control-Allow-Credentials=true, got $allowCredentials"
        }
    }

    @Test
    fun `multiple sessions for same user are independent`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)

        val firstLogin = sessionLogin(username, password)
        val firstSession = extractSession(firstLogin)!!

        val secondLogin = sessionLogin(username, password)
        val secondSession = extractSession(secondLogin)!!

        assert(firstSession.id != secondSession.id) {
            "Expected different session IDs for independent logins"
        }
    }

    @Test
    fun `session login with TOTP enabled requires code`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)

        enrollAndEnableTotp(username, password)

        val result = sessionLogin(username, password)

        assert(result.response.status == 200) { "Expected 200 but got ${result.response.status}" }
        val json = objectMapper.readTree(result.response.contentAsString)
        assert(json["totpRequired"].asBoolean()) { "Expected totpRequired=true" }
        assert(!json["success"].asBoolean()) { "Expected success=false when TOTP is required" }
    }

    @Test
    fun `session login with valid TOTP creates session`() {
        val username = uniqueUsername()
        val password = "securepass123"
        registerAndConfirmUser(username, password)

        val secret = enrollAndEnableTotp(username, password)

        val totpCode = generateTotpCode(secret)
        val result = sessionLogin(username, password, totpCode)

        assert(result.response.status == 200) { "Expected 200 but got ${result.response.status}" }
        val json = objectMapper.readTree(result.response.contentAsString)
        assert(json["success"].asBoolean()) { "Expected success=true" }
        val session = extractSession(result)
        assert(session != null) { "Expected HttpSession to be created after TOTP session login" }
    }

    @Test
    fun `without session authorize endpoint does not issue code`() {
        val result =
            mockMvc
                .get("/oauth2/authorize") {
                    param("response_type", "code")
                    param("client_id", "auth-ui")
                    param("redirect_uri", "http://localhost:5174/callback")
                    param("scope", "openid")
                    param("code_challenge", "test-challenge")
                    param("code_challenge_method", "S256")
                    accept = MediaType.TEXT_HTML
                }.andReturn()

        // Without session: redirect to login (302) or error (400/401)
        val status = result.response.status
        val location = result.response.getHeader("Location")
        // Should NOT issue an authorization code without authentication
        if (location != null) {
            assert(!location.contains("code=")) {
                "Expected no authorization code without authentication, got Location: $location"
            }
        }
        assert(status != 200) { "Expected non-200 status without authentication" }
    }
}
