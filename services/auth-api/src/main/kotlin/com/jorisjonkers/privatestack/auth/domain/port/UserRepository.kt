package com.jorisjonkers.privatestack.auth.domain.port

import com.jorisjonkers.privatestack.auth.domain.model.User
import com.jorisjonkers.privatestack.auth.domain.model.UserId

interface UserRepository {
    fun findById(id: UserId): User?
    fun findByUsername(username: String): User?
    fun save(user: User): User
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}
