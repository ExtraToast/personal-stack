package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.application.query.GetUserQueryService
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.SessionUserResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class MeController(
    private val getUserQueryService: GetUserQueryService,
) {
    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<SessionUserResponse> {
        val fullUser = getUserQueryService.findById(user.userIdValue())
        return ResponseEntity.ok(
            SessionUserResponse(
                id = user.userId.toString(),
                username = user.username,
                email = fullUser.email,
                firstName = fullUser.firstName,
                lastName = fullUser.lastName,
                role =
                    user.roles
                        .firstOrNull { it.startsWith("ROLE_") }
                        ?.removePrefix("ROLE_") ?: "USER",
                roles = user.roles,
            ),
        )
    }
}
