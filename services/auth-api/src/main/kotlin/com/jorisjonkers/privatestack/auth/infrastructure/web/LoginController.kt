package com.jorisjonkers.privatestack.auth.infrastructure.web

import com.jorisjonkers.privatestack.auth.domain.model.UserCredentials
import com.jorisjonkers.privatestack.auth.domain.model.UserId
import com.jorisjonkers.privatestack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.privatestack.auth.domain.port.UserRepository
import com.jorisjonkers.privatestack.auth.infrastructure.security.TokenService
import com.jorisjonkers.privatestack.auth.infrastructure.web.dto.LoginRequest
import com.jorisjonkers.privatestack.auth.infrastructure.web.dto.RefreshRequest
import com.jorisjonkers.privatestack.auth.infrastructure.web.dto.TokenResponse
import com.jorisjonkers.privatestack.common.exception.DomainException
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
    private val jwtDecoder: JwtDecoder,
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<TokenResponse> {
        val credentials = userRepository.findCredentialsByUsername(request.username)
            ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(request.password, credentials.passwordHash)) {
            throw InvalidCredentialsException()
        }

        val userId = credentials.userId.value.toString()
        val roles = listOf("ROLE_${credentials.role.name}")
        val response = TokenResponse(
            accessToken = tokenService.createAccessToken(credentials.username, userId, roles),
            refreshToken = tokenService.createRefreshToken(userId),
            expiresIn = 900,
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<TokenResponse> {
        val credentials = resolveRefreshCredentials(request.refreshToken)

        val userId = credentials.userId.value.toString()
        val roles = listOf("ROLE_${credentials.role.name}")
        val response = TokenResponse(
            accessToken = tokenService.createAccessToken(credentials.username, userId, roles),
            refreshToken = tokenService.createRefreshToken(userId),
            expiresIn = 900,
        )
        return ResponseEntity.ok(response)
    }

    private fun resolveRefreshCredentials(refreshToken: String): UserCredentials {
        val jwt = decodeRefreshToken(refreshToken)

        val user = userRepository.findById(UserId(UUID.fromString(jwt.subject)))
            ?: throw InvalidCredentialsException()

        return userRepository.findCredentialsByUsername(user.username)
            ?: throw InvalidCredentialsException()
    }

    private fun decodeRefreshToken(refreshToken: String): Jwt {
        val jwt = try {
            jwtDecoder.decode(refreshToken)
        } catch (e: JwtException) {
            throw InvalidCredentialsException(e)
        }

        if (jwt.getClaim<String>("type") != "refresh") {
            throw InvalidCredentialsException()
        }

        return jwt
    }
}

class InvalidCredentialsException(cause: Throwable? = null) :
    DomainException("Invalid username or password", "INVALID_CREDENTIALS", cause)
