package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.SessionUserResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class MeController {
    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<SessionUserResponse> =
        ResponseEntity.ok(
            SessionUserResponse(
                id = user.userId.toString(),
                username = user.username,
                role =
                    user.roles
                        .firstOrNull { it.startsWith("ROLE_") }
                        ?.removePrefix("ROLE_") ?: "USER",
                roles = user.roles,
            ),
        )
}
