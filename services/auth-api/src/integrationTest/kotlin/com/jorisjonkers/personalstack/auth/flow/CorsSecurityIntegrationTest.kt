package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.options
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

class CorsSecurityIntegrationTest : IntegrationTestBase() {
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
    fun `allowed origin receives CORS headers`() {
        mockMvc
            .post("/api/v1/auth/login") {
                header(HttpHeaders.ORIGIN, "http://localhost:5174")
                header(HttpHeaders.CONTENT_TYPE, "application/json")
                content = """{"username":"nouser","password":"nopass"}"""
            }.andExpect {
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5174") }
            }
    }

    @Test
    fun `disallowed origin does not receive CORS headers`() {
        mockMvc
            .post("/api/v1/auth/login") {
                header(HttpHeaders.ORIGIN, "https://evil.example.com")
                header(HttpHeaders.CONTENT_TYPE, "application/json")
                content = """{"username":"nouser","password":"nopass"}"""
            }.andExpect {
                header { doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN) }
            }
    }

    @Test
    fun `preflight request for allowed origin returns 200`() {
        mockMvc
            .options("/api/v1/auth/login") {
                header(HttpHeaders.ORIGIN, "http://localhost:5174")
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
            }.andExpect {
                status { isOk() }
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5174") }
                header { exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS) }
            }
    }

    @Test
    fun `credentials are allowed in CORS response`() {
        mockMvc
            .post("/api/v1/auth/login") {
                header(HttpHeaders.ORIGIN, "http://localhost:5174")
                header(HttpHeaders.CONTENT_TYPE, "application/json")
                content = """{"username":"nouser","password":"nopass"}"""
            }.andExpect {
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true") }
            }
    }
}
