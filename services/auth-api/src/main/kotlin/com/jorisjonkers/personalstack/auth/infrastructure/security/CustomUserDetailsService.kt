package com.jorisjonkers.personalstack.auth.infrastructure.security

import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
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

        return AuthenticatedUser(
            userId = credentials.userId,
            username = credentials.username,
            roles = buildRoles(credentials),
            passwordHash = credentials.passwordHash,
        )
    }

    private fun buildRoles(credentials: UserCredentials): List<String> =
        buildList {
            add("ROLE_${credentials.role.name}")
            if (credentials.role == Role.ADMIN) {
                addAll(ServicePermission.entries.map { "SERVICE_${it.name}" })
            } else {
                addAll(credentials.servicePermissions.map { "SERVICE_${it.name}" })
            }
        }
}
