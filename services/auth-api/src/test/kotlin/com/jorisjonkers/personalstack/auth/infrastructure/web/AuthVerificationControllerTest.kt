package com.jorisjonkers.personalstack.auth.infrastructure.web

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt

class AuthVerificationControllerTest {
    private val controller = AuthVerificationController()

    private fun buildJwt(
        subject: String = "user-123",
        roles: List<String> = listOf("ROLE_USER"),
    ): Jwt {
        val jwt = mockk<Jwt>()
        every { jwt.subject } returns subject
        every { jwt.getClaimAsStringList("roles") } returns roles
        return jwt
    }

    @Test
    fun `verify returns 200 with user headers for valid JWT`() {
        val jwt = buildJwt(roles = listOf("ROLE_USER", "SERVICE_VAULT"))

        val response = controller.verify(jwt, "vault.jorisjonkers.dev")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers["X-User-Id"]).containsExactly("user-123")
        assertThat(response.headers["X-User-Roles"]).containsExactly("ROLE_USER,SERVICE_VAULT")
    }

    @Test
    fun `verify returns 403 when USER lacks service permission`() {
        val jwt = buildJwt(roles = listOf("ROLE_USER"))

        val response = controller.verify(jwt, "vault.jorisjonkers.dev")

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `verify returns 200 for ADMIN regardless of service`() {
        val jwt = buildJwt(roles = listOf("ROLE_ADMIN"))

        val response = controller.verify(jwt, "vault.jorisjonkers.dev")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers["X-User-Id"]).containsExactly("user-123")
    }

    @Test
    fun `verify returns 200 when no X-Forwarded-Host present`() {
        val jwt = buildJwt(roles = listOf("ROLE_USER"))

        val response = controller.verify(jwt, null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers["X-User-Id"]).containsExactly("user-123")
    }

    @Test
    fun `verify propagates X-User-Id from JWT subject`() {
        val jwt = buildJwt(subject = "abc-def-ghi")

        val response = controller.verify(jwt, null)

        assertThat(response.headers["X-User-Id"]).containsExactly("abc-def-ghi")
    }

    @Test
    fun `verify propagates X-User-Roles header`() {
        val jwt = buildJwt(roles = listOf("ROLE_USER", "SERVICE_MAIL"))

        val response = controller.verify(jwt, null)

        assertThat(response.headers["X-User-Roles"]).containsExactly("ROLE_USER,SERVICE_MAIL")
    }

    @Test
    fun `verify with multiple roles joins them with comma`() {
        val jwt = buildJwt(roles = listOf("ROLE_USER", "SERVICE_VAULT", "SERVICE_GRAFANA"))

        val response = controller.verify(jwt, null)

        assertThat(response.headers["X-User-Roles"])
            .containsExactly("ROLE_USER,SERVICE_VAULT,SERVICE_GRAFANA")
    }

    @Test
    fun `unknown subdomain host passes through without permission check`() {
        val jwt = buildJwt(roles = listOf("ROLE_USER"))

        val response = controller.verify(jwt, "unknown-service.jorisjonkers.dev")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers["X-User-Id"]).containsExactly("user-123")
    }
}
