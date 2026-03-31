package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.application.command.UpdateProfileCommand
import com.jorisjonkers.personalstack.auth.application.query.GetUserQueryService
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.ProfileResponse
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.UpdateProfileRequest
import com.jorisjonkers.personalstack.common.command.CommandBus
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me")
class ProfileController(
    private val commandBus: CommandBus,
    private val getUserQueryService: GetUserQueryService,
) {
    @PatchMapping
    fun updateProfile(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ProfileResponse {
        commandBus.dispatch(
            UpdateProfileCommand(
                userId = user.userIdValue(),
                firstName = request.firstName,
                lastName = request.lastName,
            ),
        )
        val updated = getUserQueryService.findById(user.userIdValue())
        return ProfileResponse.from(updated)
    }
}
