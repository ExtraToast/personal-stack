package com.jorisjonkers.personalstack.auth.infrastructure.security

import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val credentials =
            userRepository.findCredentialsByUsername(username)
                ?: throw UsernameNotFoundException("User not found: $username")

        return User(
            credentials.username,
            credentials.passwordHash,
            listOf(SimpleGrantedAuthority("ROLE_${credentials.role.name}")),
        )
    }
}
