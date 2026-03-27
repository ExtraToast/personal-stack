package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.application.command.InvalidTotpCodeException
import com.jorisjonkers.personalstack.auth.application.command.InvalidTotpStateException
import com.jorisjonkers.personalstack.auth.domain.exception.EmailNotConfirmedException
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.auth.infrastructure.security.TokenService
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.LoginRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.LoginResponse
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.RefreshRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.TokenResponse
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.TotpChallengeRequest
import com.jorisjonkers.personalstack.common.exception.DomainException
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
class LoginController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val totpService: TotpService,
    private val jwtDecoder: JwtDecoder,
) {
    @Suppress("ThrowsCount")
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<LoginResponse> {
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
            val challengeToken =
                tokenService.createTotpChallengeToken(
                    userId = credentials.userId.value.toString(),
                    username = credentials.username,
                )
            return ResponseEntity.ok(
                LoginResponse(
                    totpRequired = true,
                    totpChallengeToken = challengeToken,
                ),
            )
        }

        return ResponseEntity.ok(issueFullTokens(credentials))
    }

    @Suppress("ThrowsCount")
    @PostMapping("/totp-challenge")
    fun totpChallenge(
        @Valid @RequestBody request: TotpChallengeRequest,
    ): ResponseEntity<LoginResponse> {
        val jwt = decodeChallengeToken(request.totpChallengeToken)
        val userId = UserId(UUID.fromString(jwt.subject))

        val credentials =
            userRepository.findCredentialsByUsername(jwt.getClaim("username"))
                ?: throw InvalidCredentialsException()

        if (credentials.userId != userId) {
            throw InvalidCredentialsException()
        }

        val totpSecret =
            credentials.totpSecret
                ?: throw InvalidTotpStateException("TOTP not configured for this account")

        if (!totpService.verifyCode(totpSecret, request.code)) {
            throw InvalidTotpCodeException()
        }

        return ResponseEntity.ok(issueFullTokens(credentials))
    }

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): ResponseEntity<TokenResponse> {
        val credentials = resolveRefreshCredentials(request.refreshToken)

        val userId = credentials.userId.value.toString()
        val roles = listOf("ROLE_${credentials.role.name}")
        val response =
            TokenResponse(
                accessToken = tokenService.createAccessToken(credentials.username, userId, roles),
                refreshToken = tokenService.createRefreshToken(userId),
                expiresIn = 900,
            )
        return ResponseEntity.ok(response)
    }

    private fun issueFullTokens(credentials: UserCredentials): LoginResponse {
        val userId = credentials.userId.value.toString()
        val roles = listOf("ROLE_${credentials.role.name}")
        return LoginResponse(
            totpRequired = false,
            accessToken = tokenService.createAccessToken(credentials.username, userId, roles),
            refreshToken = tokenService.createRefreshToken(userId),
            expiresIn = 900,
        )
    }

    private fun resolveRefreshCredentials(refreshToken: String): UserCredentials {
        val jwt = decodeRefreshToken(refreshToken)

        val user =
            userRepository.findById(UserId(UUID.fromString(jwt.subject)))
                ?: throw InvalidCredentialsException()

        return userRepository.findCredentialsByUsername(user.username)
            ?: throw InvalidCredentialsException()
    }

    private fun decodeRefreshToken(refreshToken: String): Jwt {
        val jwt =
            try {
                jwtDecoder.decode(refreshToken)
            } catch (e: JwtException) {
                throw InvalidCredentialsException(e)
            }

        if (jwt.getClaim<String>("type") != "refresh") {
            throw InvalidCredentialsException()
        }

        return jwt
    }

    private fun decodeChallengeToken(token: String): Jwt {
        val jwt =
            try {
                jwtDecoder.decode(token)
            } catch (e: JwtException) {
                throw InvalidCredentialsException(e)
            }

        if (jwt.getClaim<String>("type") != "totp_challenge") {
            throw InvalidCredentialsException()
        }

        return jwt
    }
}

class InvalidCredentialsException(
    cause: Throwable? = null,
) : DomainException("Invalid username or password", "INVALID_CREDENTIALS", cause)
