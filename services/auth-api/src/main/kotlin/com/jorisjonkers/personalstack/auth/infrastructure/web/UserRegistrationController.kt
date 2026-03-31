package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.application.command.RegisterUserCommand
import com.jorisjonkers.personalstack.auth.application.query.GetUserQueryService
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.RegisterUserRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.UserResponse
import com.jorisjonkers.personalstack.common.command.CommandBus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserRegistrationController(
    private val commandBus: CommandBus,
    private val getUserQueryService: GetUserQueryService,
) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Valid @RequestBody request: RegisterUserRequest,
    ): UserResponse {
        val command =
            RegisterUserCommand(
                username = request.username,
                email = request.email,
                firstName = request.firstName,
                lastName = request.lastName,
                password = request.password,
            )
        commandBus.dispatch(command)
        val user = getUserQueryService.findByUsername(request.username)
        return UserResponse.from(user)
    }
}
