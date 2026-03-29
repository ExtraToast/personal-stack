package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.domain.exception.EmailNotConfirmedException
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.SessionLoginRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.SessionLoginResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Establishes a server-side session after authenticating the user.
 *
 * This endpoint bridges the stateless REST login with the OAuth2 Authorization
 * Server, which requires an authenticated [jakarta.servlet.http.HttpSession].
 * After the session is created the browser can follow the OAuth2 authorize
 * redirect and Spring will recognise the user.
 */
@RestController
@RequestMapping("/api/v1/auth")
class SessionLoginController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val totpService: TotpService,
) {
    @Suppress("ThrowsCount")
    @PostMapping("/session-login")
    fun sessionLogin(
        @Valid @RequestBody request: SessionLoginRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<SessionLoginResponse> {
        val credentials =
            userRepository.findCredentialsByUsername(request.username)
                ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(request.password, credentials.passwordHash)) {
            throw InvalidCredentialsException()
        }

        if (!credentials.emailConfirmed) {
            throw EmailNotConfirmedException()
        }

        if (credentials.totpEnabled) {
            if (request.totpCode == null) {
                return ResponseEntity.ok(SessionLoginResponse(totpRequired = true))
            }

            val totpSecret =
                credentials.totpSecret
                    ?: throw InvalidCredentialsException()

            if (!totpService.verifyCode(totpSecret, request.totpCode)) {
                throw com.jorisjonkers.personalstack.auth.application.command
                    .InvalidTotpCodeException()
            }
        }

        establishSession(credentials, httpRequest)

        return ResponseEntity.ok(SessionLoginResponse(success = true))
    }

    private fun establishSession(
        credentials: UserCredentials,
        httpRequest: HttpServletRequest,
    ) {
        val authorities = buildRoles(credentials).map { SimpleGrantedAuthority(it) }
        val authentication =
            UsernamePasswordAuthenticationToken(credentials.username, null, authorities)

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        val session = httpRequest.getSession(true)
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            context,
        )
    }

    private fun buildRoles(credentials: UserCredentials): List<String> =
        buildList {
            add("ROLE_${credentials.role.name}")
            if (credentials.role == Role.ADMIN) {
                addAll(ServicePermission.entries.map { "SERVICE_${it.name}" })
            } else {
                addAll(credentials.servicePermissions.map { "SERVICE_${it.name}" })
            }
        }
}
