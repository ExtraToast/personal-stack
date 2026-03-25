package com.jorisjonkers.privatestack.auth.persistence

import com.jorisjonkers.privatestack.auth.IntegrationTestBase
import com.jorisjonkers.privatestack.auth.domain.model.Role
import com.jorisjonkers.privatestack.auth.domain.model.User
import com.jorisjonkers.privatestack.auth.domain.model.UserId
import com.jorisjonkers.privatestack.auth.domain.port.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

class JooqUserRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `create and findByUsername returns the saved user`() {
        val user = buildUser(username = "alice", email = "alice@example.com")
        userRepository.create(user, "\$2a\$10\$hashedPassword")

        val found = userRepository.findByUsername("alice")

        assertThat(found).isNotNull
        assertThat(found!!.username).isEqualTo("alice")
        assertThat(found.email).isEqualTo("alice@example.com")
        assertThat(found.role).isEqualTo(Role.USER)
        assertThat(found.totpEnabled).isFalse()
    }

    @Test
    fun `findById returns the saved user`() {
        val user = buildUser(username = "bob", email = "bob@example.com")
        userRepository.create(user, "\$2a\$10\$hashedPassword")

        val found = userRepository.findById(user.id)

        assertThat(found).isNotNull
        assertThat(found!!.id).isEqualTo(user.id)
    }

    @Test
    fun `findCredentialsByUsername returns credentials with password hash`() {
        val user = buildUser(username = "carol", email = "carol@example.com")
        userRepository.create(user, "\$2a\$10\$expectedHash")

        val credentials = userRepository.findCredentialsByUsername("carol")

        assertThat(credentials).isNotNull
        assertThat(credentials!!.username).isEqualTo("carol")
        assertThat(credentials.passwordHash).isEqualTo("\$2a\$10\$expectedHash")
        assertThat(credentials.totpEnabled).isFalse()
    }

    @Test
    fun `existsByUsername returns true when user exists`() {
        val user = buildUser(username = "dave", email = "dave@example.com")
        userRepository.create(user, "\$2a\$10\$hash")

        assertThat(userRepository.existsByUsername("dave")).isTrue()
        assertThat(userRepository.existsByUsername("unknown")).isFalse()
    }

    @Test
    fun `existsByEmail returns true when email is registered`() {
        val user = buildUser(username = "eve", email = "eve@example.com")
        userRepository.create(user, "\$2a\$10\$hash")

        assertThat(userRepository.existsByEmail("eve@example.com")).isTrue()
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse()
    }

    @Test
    fun `update persists role and totpEnabled changes`() {
        val user = buildUser(username = "frank", email = "frank@example.com")
        userRepository.create(user, "\$2a\$10\$hash")

        val updated = user.copy(totpEnabled = true, role = Role.ADMIN)
        userRepository.update(updated)

        val found = userRepository.findById(user.id)!!
        assertThat(found.totpEnabled).isTrue()
        assertThat(found.role).isEqualTo(Role.ADMIN)
    }

    private fun buildUser(username: String, email: String): User {
        val now = Instant.now()
        return User(
            id = UserId(UUID.randomUUID()),
            username = username,
            email = email,
            role = Role.USER,
            totpEnabled = false,
            createdAt = now,
            updatedAt = now,
        )
    }
}
