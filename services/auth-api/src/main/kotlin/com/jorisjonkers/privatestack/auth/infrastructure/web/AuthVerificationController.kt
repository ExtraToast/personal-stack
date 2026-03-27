package com.jorisjonkers.privatestack.auth.infrastructure.web

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Forward-auth endpoint consumed by Traefik's forwardAuth middleware.
 * Validates the JWT and propagates user identity via response headers so
 * downstream services can trust the caller without re-verifying the token.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthVerificationController {
    @GetMapping("/verify")
    fun verify(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        val userId = jwt.subject
        val roles = jwt.getClaimAsStringList("roles")?.joinToString(",") ?: ""

        return ResponseEntity
            .ok()
            .header("X-User-Id", userId)
            .header("X-User-Roles", roles)
            .build()
    }
}
