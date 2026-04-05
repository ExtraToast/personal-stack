package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
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
                header(HttpHeaders.ORIGIN, "https://vault.jorisjonkers.test")
                header(HttpHeaders.CONTENT_TYPE, "application/json")
                content = """{"username":"nouser","password":"nopass"}"""
            }.andExpect {
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://vault.jorisjonkers.test") }
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
                header(HttpHeaders.ORIGIN, "https://grafana.jorisjonkers.test")
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
            }.andExpect {
                status { isOk() }
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://grafana.jorisjonkers.test") }
                header { exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS) }
            }
    }

    @Test
    fun `credentials are allowed in CORS response`() {
        mockMvc
            .post("/api/v1/auth/login") {
                header(HttpHeaders.ORIGIN, "https://n8n.jorisjonkers.test")
                header(HttpHeaders.CONTENT_TYPE, "application/json")
                content = """{"username":"nouser","password":"nopass"}"""
            }.andExpect {
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true") }
            }
    }

    @Test
    fun `nomad origin receives CORS headers on login endpoint`() {
        mockMvc
            .post("/api/v1/auth/login") {
                header(HttpHeaders.ORIGIN, "https://nomad.jorisjonkers.test")
                header(HttpHeaders.CONTENT_TYPE, "application/json")
                content = """{"username":"nouser","password":"nopass"}"""
            }.andExpect {
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://nomad.jorisjonkers.test") }
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true") }
            }
    }

    @Test
    fun `forward auth preflight allows downstream service origins`() {
        val response =
            mockMvc
                .options("/api/v1/auth/verify") {
                    header(HttpHeaders.ORIGIN, "https://mail.jorisjonkers.test")
                    header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
                }.andExpect {
                    status { isOk() }
                    header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://mail.jorisjonkers.test") }
                    header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true") }
                }.andReturn()

        assertThat(response.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS).orEmpty()).contains("GET")
    }

    @Test
    fun `forward auth preflight allows nomad origin`() {
        val response =
            mockMvc
                .options("/api/v1/auth/verify") {
                    header(HttpHeaders.ORIGIN, "https://nomad.jorisjonkers.test")
                    header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
                }.andExpect {
                    status { isOk() }
                    header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://nomad.jorisjonkers.test") }
                    header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true") }
                }.andReturn()

        assertThat(response.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS).orEmpty()).contains("GET")
    }
}
