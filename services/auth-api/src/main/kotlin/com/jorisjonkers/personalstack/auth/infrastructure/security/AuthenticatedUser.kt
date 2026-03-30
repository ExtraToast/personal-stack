package com.jorisjonkers.personalstack.auth.infrastructure.security

import com.jorisjonkers.personalstack.auth.domain.model.UserId
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

data class AuthenticatedUser(
    val userId: UUID,
    private val username: String,
    val roles: List<String>,
    private val passwordHash: String = "",
) : UserDetails {
    fun userIdValue(): UserId = UserId(userId)

    override fun getUsername(): String = username

    override fun getPassword(): String = passwordHash

    override fun getAuthorities(): Collection<GrantedAuthority> = roles.map { SimpleGrantedAuthority(it) }

    companion object {
        fun of(
            userId: UserId,
            username: String,
            roles: List<String>,
            passwordHash: String = "",
        ): AuthenticatedUser = AuthenticatedUser(userId.value, username, roles, passwordHash)
    }
}
