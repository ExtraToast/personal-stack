package com.jorisjonkers.privatestack.auth.infrastructure.persistence

import com.jorisjonkers.privatestack.auth.domain.model.User
import com.jorisjonkers.privatestack.auth.domain.model.UserId
import com.jorisjonkers.privatestack.auth.domain.port.UserRepository
import org.springframework.stereotype.Repository

@Repository
class JooqUserRepository : UserRepository {

    override fun findById(id: UserId): User? {
        TODO("Implement with jOOQ after code generation")
    }

    override fun findByUsername(username: String): User? {
        TODO("Implement with jOOQ after code generation")
    }

    override fun save(user: User): User {
        TODO("Implement with jOOQ after code generation")
    }

    override fun existsByUsername(username: String): Boolean {
        TODO("Implement with jOOQ after code generation")
    }

    override fun existsByEmail(email: String): Boolean {
        TODO("Implement with jOOQ after code generation")
    }
}
