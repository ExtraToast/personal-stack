package com.jorisjonkers.privatestack.auth.flow

import com.jorisjonkers.privatestack.auth.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
class ForwardAuthIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `verify endpoint returns 200 with user identity headers for valid JWT`() {
        mockMvc.get("/api/v1/auth/verify") {
            with(jwt().jwt { jwt ->
                jwt.subject("user-123")
                jwt.claim("roles", listOf("USER"))
            })
        }.andExpect {
            status { isOk() }
            header { string("X-User-Id", "user-123") }
            header { string("X-User-Roles", "USER") }
        }
    }

    @Test
    fun `verify endpoint returns 401 without authentication`() {
        mockMvc.get("/api/v1/auth/verify") {
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
