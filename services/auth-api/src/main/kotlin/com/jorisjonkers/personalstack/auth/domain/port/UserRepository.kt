package com.jorisjonkers.personalstack.auth.domain.port

import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.model.UserId

interface UserRepository {
    fun findById(id: UserId): User?

    fun findByUsername(username: String): User?

    fun findByEmail(email: String): User?

    fun findCredentialsByUsername(username: String): UserCredentials?

    fun findAll(): List<User>

    fun create(
        user: User,
        passwordHash: String,
    ): User

    fun update(user: User): User

    fun saveServicePermissions(
        userId: UserId,
        permissions: Set<ServicePermission>,
    )

    fun deleteById(id: UserId)

    fun saveTotpSecret(
        userId: UserId,
        secret: String,
    )

    fun existsByUsername(username: String): Boolean

    fun existsByEmail(email: String): Boolean
}
