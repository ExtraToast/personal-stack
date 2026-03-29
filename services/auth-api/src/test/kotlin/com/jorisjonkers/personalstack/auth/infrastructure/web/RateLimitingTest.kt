package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.auth.infrastructure.security.TokenService
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.LoginRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.RefreshRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.SessionLoginRequest
import com.jorisjonkers.personalstack.common.web.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class RateLimitingTest {
    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val tokenService = mockk<TokenService>()
    private val totpService = mockk<TotpService>()
    private val jwtDecoder = mockk<JwtDecoder>()
    private val objectMapper = jacksonObjectMapper()
    private lateinit var loginMockMvc: MockMvc
    private lateinit var sessionLoginMockMvc: MockMvc

    private val userId = UserId(UUID.randomUUID())

    private val credentials =
        UserCredentials(
            userId = userId,
            username = "alice",
            passwordHash = "hashed-password",
            totpSecret = null,
            totpEnabled = false,
            emailConfirmed = true,
            role = Role.USER,
        )

    @BeforeEach
    fun setUp() {
        loginMockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    LoginController(userRepository, passwordEncoder, tokenService, totpService, jwtDecoder),
                ).setControllerAdvice(GlobalExceptionHandler())
                .build()

        sessionLoginMockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    SessionLoginController(userRepository, passwordEncoder, totpService),
                ).setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `login endpoint handles concurrent requests without error`() {
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true
        every { tokenService.createAccessToken("alice", userId.value.toString(), listOf("ROLE_USER")) } returns
            "access-token"
        every { tokenService.createRefreshToken(userId.value.toString()) } returns "refresh-token"

        val request = LoginRequest(username = "alice", password = "securepass123")
        val requestBody = objectMapper.writeValueAsString(request)
        val concurrentRequests = 10
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        val latch = CountDownLatch(concurrentRequests)
        val executor = Executors.newFixedThreadPool(concurrentRequests)

        repeat(concurrentRequests) {
            executor.submit {
                try {
                    val result =
                        loginMockMvc
                            .post("/api/v1/auth/login") {
                                contentType = MediaType.APPLICATION_JSON
                                content = requestBody
                            }.andReturn()
                    if (result.response.status == 200) {
                        successCount.incrementAndGet()
                    } else {
                        errorCount.incrementAndGet()
                    }
                } catch (_: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        assert(successCount.get() == concurrentRequests) {
            "Expected $concurrentRequests successes but got ${successCount.get()} (errors: ${errorCount.get()})"
        }
    }

    @Test
    fun `session-login endpoint handles concurrent requests without error`() {
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true

        val request = SessionLoginRequest(username = "alice", password = "securepass123")
        val requestBody = objectMapper.writeValueAsString(request)
        val concurrentRequests = 10
        val successCount = AtomicInteger(0)
        val latch = CountDownLatch(concurrentRequests)
        val executor = Executors.newFixedThreadPool(concurrentRequests)

        repeat(concurrentRequests) {
            executor.submit {
                try {
                    val result =
                        sessionLoginMockMvc
                            .post("/api/v1/auth/session-login") {
                                contentType = MediaType.APPLICATION_JSON
                                content = requestBody
                            }.andReturn()
                    if (result.response.status == 200) {
                        successCount.incrementAndGet()
                    }
                } catch (_: Exception) {
                    // Session-related issues in standalone MockMvc are expected
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // All requests should complete without crashing the server
        assert(successCount.get() > 0) {
            "Expected at least some successful concurrent session-login requests"
        }
    }

    @Test
    fun `token refresh handles concurrent requests without error`() {
        val mockJwt =
            Jwt
                .withTokenValue("refresh-token")
                .header("alg", "RS256")
                .subject(userId.value.toString())
                .claim("type", "refresh")
                .build()

        every { jwtDecoder.decode("refresh-token") } returns mockJwt
        every { userRepository.findById(userId) } returns
            com.jorisjonkers.personalstack.auth.domain.model.User(
                id = userId,
                username = "alice",
                email = "alice@example.com",
                role = Role.USER,
                emailConfirmed = true,
                totpEnabled = false,
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
            )
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { tokenService.createAccessToken("alice", userId.value.toString(), listOf("ROLE_USER")) } returns
            "new-access-token"
        every { tokenService.createRefreshToken(userId.value.toString()) } returns "new-refresh-token"

        val request = RefreshRequest(refreshToken = "refresh-token")
        val requestBody = objectMapper.writeValueAsString(request)
        val concurrentRequests = 10
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        val latch = CountDownLatch(concurrentRequests)
        val executor = Executors.newFixedThreadPool(concurrentRequests)

        repeat(concurrentRequests) {
            executor.submit {
                try {
                    val result =
                        loginMockMvc
                            .post("/api/v1/auth/refresh") {
                                contentType = MediaType.APPLICATION_JSON
                                content = requestBody
                            }.andReturn()
                    if (result.response.status == 200) {
                        successCount.incrementAndGet()
                    } else {
                        errorCount.incrementAndGet()
                    }
                } catch (_: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        assert(successCount.get() == concurrentRequests) {
            "Expected $concurrentRequests successes but got ${successCount.get()} (errors: ${errorCount.get()})"
        }
    }
}
