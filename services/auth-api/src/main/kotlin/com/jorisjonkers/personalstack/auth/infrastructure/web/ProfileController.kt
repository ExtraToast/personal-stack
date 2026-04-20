package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.application.command.UpdateProfileCommand
import com.jorisjonkers.personalstack.auth.application.query.GetUserQueryService
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.ProfileResponse
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.UpdateProfileRequest
import com.jorisjonkers.personalstack.common.command.CommandBus
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
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
    // The app-ui account page loads its data from here on mount. Without
    // this handler the endpoint 404'd and the view rendered a generic
    // failure banner instead of the profile form. MeController at
    // /api/v1/auth/me returns a session-shaped response for cross-app
    // auth and has a different DTO contract, so we can't reuse that.
    @GetMapping
    fun getProfile(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ProfileResponse = ProfileResponse.from(getUserQueryService.findById(user.userIdValue()))

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
