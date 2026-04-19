package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.domain.exception.EmailNotConfirmedException
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.SessionLoginRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.SessionLoginResponse
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.SessionUserResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/api/v1/auth")
class SessionLoginController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val totpService: TotpService,
    @param:Value("\${session.timeout:30d}")
    private val sessionTimeout: Duration,
) {
    @PostMapping("/session-login")
    fun sessionLogin(
        @Valid @RequestBody request: SessionLoginRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<SessionLoginResponse> {
        val credentials = authenticate(request.username, request.password)

        val totpResult = handleTotp(credentials, request.totpCode)
        if (totpResult != null) return totpResult

        establishSession(credentials, httpRequest)

        return ResponseEntity.ok(
            SessionLoginResponse(
                success = true,
                user =
                    SessionUserResponse(
                        id = credentials.userId.value.toString(),
                        username = credentials.username,
                        role = credentials.role.name,
                    ),
            ),
        )
    }

    @Suppress("ThrowsCount")
    private fun authenticate(
        username: String,
        password: String,
    ): UserCredentials {
        val credentials =
            userRepository.findCredentialsByUsername(username)
                ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(password, credentials.passwordHash)) {
            throw InvalidCredentialsException()
        }
        if (!credentials.emailConfirmed) throw EmailNotConfirmedException()
        return credentials
    }

    private fun handleTotp(
        credentials: UserCredentials,
        totpCode: String?,
    ): ResponseEntity<SessionLoginResponse>? {
        if (!credentials.totpEnabled) return null
        if (totpCode == null) return ResponseEntity.ok(SessionLoginResponse(totpRequired = true))
        val secret = credentials.totpSecret ?: throw InvalidCredentialsException()
        if (!totpService.verifyCode(secret, totpCode)) {
            throw com.jorisjonkers.personalstack.auth.application.command
                .InvalidTotpCodeException()
        }
        return null
    }

    private fun establishSession(
        credentials: UserCredentials,
        httpRequest: HttpServletRequest,
    ) {
        val authenticatedUser =
            AuthenticatedUser.of(
                userId = credentials.userId,
                username = credentials.username,
                roles = buildRoles(credentials),
            )
        val authentication =
            UsernamePasswordAuthenticationToken(authenticatedUser, null, authenticatedUser.authorities)

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        val session = httpRequest.getSession(true)
        session.maxInactiveInterval = sessionTimeout.seconds.toInt()
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
