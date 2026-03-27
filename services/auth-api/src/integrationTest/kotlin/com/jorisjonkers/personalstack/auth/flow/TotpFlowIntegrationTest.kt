package com.jorisjonkers.personalstack.auth.flow

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.apache.commons.codec.binary.Base32
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
import java.util.UUID
import java.util.concurrent.TimeUnit

class TotpFlowIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var totpService: TotpService

    @Autowired
    private lateinit var dsl: org.jooq.DSLContext

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
        val table = com.jorisjonkers.personalstack.auth.jooq.tables.EmailConfirmationToken.EMAIL_CONFIRMATION_TOKEN
        val userTable = com.jorisjonkers.personalstack.auth.jooq.tables.AppUser.APP_USER
        val userId = dsl.select(userTable.ID).from(userTable).where(userTable.USERNAME.eq(username)).fetchOne(userTable.ID)!!
        val token = dsl.select(table.TOKEN).from(table).where(table.USER_ID.eq(userId)).fetchOne(table.TOKEN)!!
        mockMvc
            .get("/api/v1/auth/confirm-email") {
                param("token", token)
            }.andExpect { status { isOk() } }
    }

    private fun login(
        username: String,
        password: String,
    ): com.fasterxml.jackson.databind.JsonNode {
        val result =
            mockMvc
                .post("/api/v1/auth/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"username":"$username","password":"$password"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

        return objectMapper.readTree(result.response.contentAsString)
    }

    @Test
    fun `register and login without TOTP returns tokens directly`() {
        val username = "totp-none-${UUID.randomUUID().toString().take(8)}"
        val password = "securepass123"

        registerUser(username, password)
        confirmEmail(username)

        val json = login(username, password)

        assert(!json["totpRequired"].asBoolean()) { "Expected totpRequired=false" }
        assert(json["accessToken"] != null && !json["accessToken"].isNull) { "Expected accessToken to be set" }
        assert(json["refreshToken"] != null && !json["refreshToken"].isNull) { "Expected refreshToken to be set" }
    }

    @Test
    fun `login with TOTP enabled returns challenge token`() {
        val username = "totp-enabled-${UUID.randomUUID().toString().take(8)}"
        val password = "securepass123"

        registerUser(username, password)
        confirmEmail(username)

        val loginJson = login(username, password)
        val accessToken = loginJson["accessToken"].asText()

        // Enroll TOTP
        val enrollResult =
            mockMvc
                .post("/api/v1/totp/enroll") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect { status { isOk() } }
                .andReturn()

        val enrollJson = objectMapper.readTree(enrollResult.response.contentAsString)
        val secret = enrollJson["secret"].asText()

        // Verify TOTP to enable it
        val code = generateTotpCode(secret)
        mockMvc
            .post("/api/v1/totp/verify") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $accessToken")
                content = """{"code":"$code"}"""
            }.andExpect { status { isNoContent() } }

        // Login again - should now require TOTP
        val secondLoginJson = login(username, password)

        assert(secondLoginJson["totpRequired"].asBoolean()) { "Expected totpRequired=true" }
        assert(
            secondLoginJson["totpChallengeToken"] != null && !secondLoginJson["totpChallengeToken"].isNull,
        ) { "Expected totpChallengeToken to be set" }
        assert(
            secondLoginJson["accessToken"] == null || secondLoginJson["accessToken"].isNull,
        ) { "Expected accessToken to be null when TOTP is required" }
    }

    @Test
    fun `totp challenge with valid code returns tokens`() {
        val username = "totp-challenge-${UUID.randomUUID().toString().take(8)}"
        val password = "securepass123"

        registerUser(username, password)
        confirmEmail(username)

        val loginJson = login(username, password)
        val accessToken = loginJson["accessToken"].asText()

        // Enroll TOTP
        val enrollResult =
            mockMvc
                .post("/api/v1/totp/enroll") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect { status { isOk() } }
                .andReturn()

        val enrollJson = objectMapper.readTree(enrollResult.response.contentAsString)
        val secret = enrollJson["secret"].asText()

        // Verify TOTP to enable it
        val verifyCode = generateTotpCode(secret)
        mockMvc
            .post("/api/v1/totp/verify") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $accessToken")
                content = """{"code":"$verifyCode"}"""
            }.andExpect { status { isNoContent() } }

        // Login again - should require TOTP
        val secondLoginJson = login(username, password)
        val challengeToken = secondLoginJson["totpChallengeToken"].asText()

        // Complete TOTP challenge
        val challengeCode = generateTotpCode(secret)
        val challengeResult =
            mockMvc
                .post("/api/v1/auth/totp-challenge") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"totpChallengeToken":"$challengeToken","code":"$challengeCode"}"""
                }.andExpect { status { isOk() } }
                .andReturn()

        val challengeJson = objectMapper.readTree(challengeResult.response.contentAsString)

        assert(!challengeJson["totpRequired"].asBoolean()) { "Expected totpRequired=false" }
        assert(
            challengeJson["accessToken"] != null && !challengeJson["accessToken"].isNull,
        ) { "Expected accessToken to be set after TOTP challenge" }
        assert(
            challengeJson["refreshToken"] != null && !challengeJson["refreshToken"].isNull,
        ) { "Expected refreshToken to be set after TOTP challenge" }
    }

    @Test
    fun `totp challenge with invalid code returns 400`() {
        val username = "totp-invalid-${UUID.randomUUID().toString().take(8)}"
        val password = "securepass123"

        registerUser(username, password)
        confirmEmail(username)

        val loginJson = login(username, password)
        val accessToken = loginJson["accessToken"].asText()

        // Enroll TOTP
        val enrollResult =
            mockMvc
                .post("/api/v1/totp/enroll") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect { status { isOk() } }
                .andReturn()

        val enrollJson = objectMapper.readTree(enrollResult.response.contentAsString)
        val secret = enrollJson["secret"].asText()

        // Verify TOTP to enable it
        val code = generateTotpCode(secret)
        mockMvc
            .post("/api/v1/totp/verify") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $accessToken")
                content = """{"code":"$code"}"""
            }.andExpect { status { isNoContent() } }

        // Login again - should require TOTP
        val secondLoginJson = login(username, password)
        val challengeToken = secondLoginJson["totpChallengeToken"].asText()

        // Submit invalid TOTP code
        mockMvc
            .post("/api/v1/auth/totp-challenge") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"totpChallengeToken":"$challengeToken","code":"000000"}"""
            }.andExpect { status { isBadRequest() } }
    }
}
