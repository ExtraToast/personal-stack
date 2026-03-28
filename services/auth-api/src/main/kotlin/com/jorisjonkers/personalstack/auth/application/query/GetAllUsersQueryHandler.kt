package com.jorisjonkers.personalstack.auth.application.query

import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import org.springframework.stereotype.Service

@Service
class GetAllUsersQueryHandler(
    private val userRepository: UserRepository,
) {
    fun handle(): List<User> = userRepository.findAll()
}
