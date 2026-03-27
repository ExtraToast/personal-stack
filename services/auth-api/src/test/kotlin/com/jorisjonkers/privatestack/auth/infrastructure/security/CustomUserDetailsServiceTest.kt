package com.jorisjonkers.privatestack.auth.infrastructure.security

import com.jorisjonkers.privatestack.auth.domain.model.Role
import com.jorisjonkers.privatestack.auth.domain.model.UserCredentials
import com.jorisjonkers.privatestack.auth.domain.model.UserId
import com.jorisjonkers.privatestack.auth.domain.port.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.core.userdetails.UsernameNotFoundException
import java.util.UUID

class CustomUserDetailsServiceTest {
    private val userRepository = mockk<UserRepository>()
    private val service = CustomUserDetailsService(userRepository)

    @Test
    fun `loadUserByUsername returns UserDetails with correct authorities`() {
        val credentials =
            UserCredentials(
                userId = UserId(UUID.randomUUID()),
                username = "alice",
                passwordHash = "\$2a\$10\$hashed",
                totpSecret = null,
                totpEnabled = false,
                role = Role.USER,
            )
        every { userRepository.findCredentialsByUsername("alice") } returns credentials

        val userDetails = service.loadUserByUsername("alice")

        assertThat(userDetails.username).isEqualTo("alice")
        assertThat(userDetails.password).isEqualTo("\$2a\$10\$hashed")
        assertThat(userDetails.authorities.map { it.authority }).containsExactly("ROLE_USER")
    }

    @Test
    fun `loadUserByUsername throws UsernameNotFoundException when user does not exist`() {
        every { userRepository.findCredentialsByUsername("unknown") } returns null

        assertThatThrownBy { service.loadUserByUsername("unknown") }
            .isInstanceOf(UsernameNotFoundException::class.java)
    }

    @Test
    fun `loadUserByUsername maps ADMIN role correctly`() {
        val credentials =
            UserCredentials(
                userId = UserId(UUID.randomUUID()),
                username = "admin",
                passwordHash = "\$2a\$10\$hashed",
                totpSecret = null,
                totpEnabled = false,
                role = Role.ADMIN,
            )
        every { userRepository.findCredentialsByUsername("admin") } returns credentials

        val userDetails = service.loadUserByUsername("admin")

        assertThat(userDetails.authorities.map { it.authority }).containsExactly("ROLE_ADMIN")
    }
}
