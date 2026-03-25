package com.jorisjonkers.privatestack.auth.domain.port

import com.jorisjonkers.privatestack.auth.domain.model.User
import com.jorisjonkers.privatestack.auth.domain.model.UserCredentials
import com.jorisjonkers.privatestack.auth.domain.model.UserId

interface UserRepository {
    fun findById(id: UserId): User?
    fun findByUsername(username: String): User?
    fun findCredentialsByUsername(username: String): UserCredentials?
    fun create(user: User, passwordHash: String): User
    fun update(user: User): User
    fun saveTotpSecret(userId: UserId, secret: String)
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}
