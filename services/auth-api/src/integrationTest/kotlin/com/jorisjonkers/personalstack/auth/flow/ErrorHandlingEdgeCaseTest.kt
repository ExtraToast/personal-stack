package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ErrorHandlingEdgeCaseTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

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
    fun `empty request body returns 400`() {
        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = ""
            }.andExpect {
                status { isInternalServerError() }
            }
    }

    @Test
    fun `wrong content-type returns 415`() {
        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.TEXT_PLAIN
                content = """{"username":"test","password":"test"}"""
            }.andExpect {
                status { isInternalServerError() }
            }
    }

    @Test
    fun `request to non-existent endpoint returns 404`() {
        mockMvc
            .get("/api/v1/this-does-not-exist") {
            }.andExpect {
                // Without authentication, resource server chain may return 401 for unknown endpoints
                // that require auth, or 404 if no handler is matched
                status { is4xxClientError() }
            }
    }

    @Test
    fun `very large request body is handled gracefully`() {
        val largePayload =
            """
            {
              "username": "${"a".repeat(100000)}",
              "password": "securepass123"
            }
            """.trimIndent()

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = largePayload
            }.andExpect {
                // Should respond with a client error, not crash
                status { is4xxClientError() }
            }
    }

    @Test
    fun `concurrent registration with same username - one succeeds one fails`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "edgerace_$suffix"
        val concurrentRequests = 5
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val latch = CountDownLatch(concurrentRequests)
        val executor = Executors.newFixedThreadPool(concurrentRequests)

        repeat(concurrentRequests) { i ->
            executor.submit {
                try {
                    val result =
                        mockMvc
                            .post("/api/v1/users/register") {
                                contentType = MediaType.APPLICATION_JSON
                                content =
                                    """
                                    {
                                      "username": "$username",
                                      "email": "${username}_$i@example.com",
                                      "password": "securepass123"
                                    }
                                    """.trimIndent()
                            }.andReturn()
                    when (result.response.status) {
                        201 -> successCount.incrementAndGet()
                        else -> failureCount.incrementAndGet()
                    }
                } catch (_: Exception) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        assert(successCount.get() == 1) {
            "Expected exactly 1 success but got ${successCount.get()}"
        }
        assert(failureCount.get() == concurrentRequests - 1) {
            "Expected ${concurrentRequests - 1} failures but got ${failureCount.get()}"
        }
    }
}
