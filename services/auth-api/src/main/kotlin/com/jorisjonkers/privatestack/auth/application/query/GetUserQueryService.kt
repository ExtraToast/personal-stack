package com.jorisjonkers.privatestack.auth.application.query

import com.jorisjonkers.privatestack.auth.domain.model.User
import com.jorisjonkers.privatestack.auth.domain.model.UserId
import com.jorisjonkers.privatestack.auth.domain.port.UserRepository
import com.jorisjonkers.privatestack.common.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class GetUserQueryService(
    private val userRepository: UserRepository,
) {
    fun findById(id: UserId): User = userRepository.findById(id) ?: throw NotFoundException("User", id.value.toString())

    fun findByUsername(username: String): User =
        userRepository.findByUsername(username) ?: throw NotFoundException("User", username)
}
