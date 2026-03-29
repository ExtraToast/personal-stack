package com.jorisjonkers.personalstack.auth.infrastructure.security

import com.jorisjonkers.personalstack.auth.domain.model.UserId
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

data class AuthenticatedUser(
    val userId: UserId,
    private val username: String,
    val roles: List<String>,
    private val passwordHash: String = "",
) : UserDetails {
    override fun getUsername(): String = username

    override fun getPassword(): String = passwordHash

    override fun getAuthorities(): Collection<GrantedAuthority> = roles.map { SimpleGrantedAuthority(it) }
}
