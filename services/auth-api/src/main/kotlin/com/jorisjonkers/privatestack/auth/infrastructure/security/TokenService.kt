package com.jorisjonkers.privatestack.auth.infrastructure.security

import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class TokenService(
    private val jwtEncoder: JwtEncoder,
    private val authorizationServerSettings: AuthorizationServerSettings,
) {

    fun createAccessToken(username: String, userId: String, roles: List<String>): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(authorizationServerSettings.issuer)
            .subject(userId)
            .claim("username", username)
            .claim("roles", roles)
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .build()
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    fun createRefreshToken(userId: String): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(authorizationServerSettings.issuer)
            .subject(userId)
            .claim("type", "refresh")
            .issuedAt(now)
            .expiresAt(now.plus(7, ChronoUnit.DAYS))
            .build()
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }
}
