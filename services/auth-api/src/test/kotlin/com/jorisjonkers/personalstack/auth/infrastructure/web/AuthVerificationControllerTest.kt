package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class AuthVerificationControllerTest {
    private val controller = AuthVerificationController()

    private fun buildUser(
        id: String = "user-123",
        roles: List<String> = listOf("ROLE_USER"),
    ): AuthenticatedUser =
        AuthenticatedUser(
            userId = UserId(UUID.fromString(id.padStart(36, '0').let {
                if (it.contains('-')) it
                else "${it.take(8)}-${it.substring(8, 12)}-${it.substring(12, 16)}-${it.substring(16, 20)}-${it.substring(20)}"
            })),
            username = "testuser",
            roles = roles,
        )

    private val defaultUserId = UUID.randomUUID()

    private fun buildUserWithUuid(
        id: UUID = defaultUserId,
        roles: List<String> = listOf("ROLE_USER"),
    ): AuthenticatedUser =
        AuthenticatedUser(
            userId = UserId(id),
            username = "testuser",
            roles = roles,
        )

    @Test
    fun `verify returns 200 with user headers for valid session`() {
        val user = buildUserWithUuid(roles = listOf("ROLE_USER", "SERVICE_VAULT"))

        val response = controller.verify(user, "vault.jorisjonkers.dev")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers["X-User-Id"]).containsExactly(defaultUserId.toString())
        assertThat(response.headers["X-User-Roles"]).containsExactly("ROLE_USER,SERVICE_VAULT")
    }

    @Test
    fun `verify returns 403 when USER lacks service permission`() {
        val user = buildUserWithUuid(roles = listOf("ROLE_USER"))

        val response = controller.verify(user, "vault.jorisjonkers.dev")

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `verify returns 200 for ADMIN regardless of service`() {
        val user = buildUserWithUuid(roles = listOf("ROLE_ADMIN"))

        val response = controller.verify(user, "vault.jorisjonkers.dev")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `verify returns 200 when no X-Forwarded-Host present`() {
        val user = buildUserWithUuid(roles = listOf("ROLE_USER"))

        val response = controller.verify(user, null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `verify propagates X-User-Id from session principal`() {
        val id = UUID.randomUUID()
        val user = buildUserWithUuid(id = id)

        val response = controller.verify(user, null)

        assertThat(response.headers["X-User-Id"]).containsExactly(id.toString())
    }

    @Test
    fun `verify propagates X-User-Roles header`() {
        val user = buildUserWithUuid(roles = listOf("ROLE_USER", "SERVICE_MAIL"))

        val response = controller.verify(user, null)

        assertThat(response.headers["X-User-Roles"]).containsExactly("ROLE_USER,SERVICE_MAIL")
    }

    @Test
    fun `verify with multiple roles joins them with comma`() {
        val user = buildUserWithUuid(roles = listOf("ROLE_USER", "SERVICE_VAULT", "SERVICE_GRAFANA"))

        val response = controller.verify(user, null)

        assertThat(response.headers["X-User-Roles"])
            .containsExactly("ROLE_USER,SERVICE_VAULT,SERVICE_GRAFANA")
    }

    @Test
    fun `unknown subdomain host passes through without permission check`() {
        val user = buildUserWithUuid(roles = listOf("ROLE_USER"))

        val response = controller.verify(user, "unknown-service.jorisjonkers.dev")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }
}
