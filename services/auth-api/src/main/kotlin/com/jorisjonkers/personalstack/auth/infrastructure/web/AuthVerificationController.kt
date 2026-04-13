package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Forward-auth endpoint consumed by Traefik's forwardAuth middleware.
 * Validates the session and propagates user identity via response headers so
 * downstream services can trust the caller without re-verifying the session.
 *
 * When [xForwardedHost] is present, the host is resolved to a [ServicePermission].
 * If a permission is required and the user's roles do not contain either ROLE_ADMIN
 * or the corresponding SERVICE_* claim, a 403 is returned.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthVerificationController {
    @GetMapping("/verify")
    fun verify(
        @AuthenticationPrincipal user: AuthenticatedUser,
        session: HttpSession,
        @RequestHeader(value = "X-Forwarded-Host", required = false) xForwardedHost: String?,
    ): ResponseEntity<Void> {
        touchSession(session)

        val requiredPermission = ServicePermission.fromHost(xForwardedHost)
        if (requiredPermission != null && !isAuthorizedForService(user.roles, requiredPermission)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val rolesHeader = user.roles.joinToString(",")

        return ResponseEntity
            .ok()
            .header("X-User-Id", user.userId.toString())
            .header("X-User-Roles", rolesHeader)
            .build()
    }

    private fun touchSession(session: HttpSession) {
        session.setAttribute(LAST_VERIFIED_AT_SESSION_KEY, Instant.now().toEpochMilli())
    }

    private fun isAuthorizedForService(
        roles: List<String>,
        permission: ServicePermission,
    ): Boolean = roles.contains("ROLE_ADMIN") || roles.contains("SERVICE_${permission.name}")

    companion object {
        const val LAST_VERIFIED_AT_SESSION_KEY = "auth.lastVerifiedAt"
    }
}
