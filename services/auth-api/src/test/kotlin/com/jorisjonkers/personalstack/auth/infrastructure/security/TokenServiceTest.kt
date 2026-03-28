package com.jorisjonkers.personalstack.auth.infrastructure.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit

class TokenServiceTest {
    private lateinit var tokenService: TokenService
    private lateinit var jwtDecoder: NimbusJwtDecoder

    @BeforeEach
    fun setUp() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val rsaKey =
            RSAKey.Builder(keyPair.public as RSAPublicKey)
                .privateKey(keyPair.private as RSAPrivateKey)
                .build()
        val jwkSource = ImmutableJWKSet<SecurityContext>(JWKSet(rsaKey))
        val jwtEncoder = NimbusJwtEncoder(jwkSource)

        val authorizationServerSettings =
            AuthorizationServerSettings.builder()
                .issuer("https://auth.jorisjonkers.dev")
                .build()

        tokenService = TokenService(jwtEncoder, authorizationServerSettings)
        jwtDecoder = NimbusJwtDecoder.withPublicKey(keyPair.public as RSAPublicKey).build()
    }

    @Test
    fun `access token has 15-minute expiry`() {
        val token = tokenService.createAccessToken("alice", "user-123", listOf("ROLE_USER"))

        val jwt = jwtDecoder.decode(token)
        val expiresAt = jwt.expiresAt!!
        val issuedAt = jwt.issuedAt!!

        val diffMinutes = ChronoUnit.MINUTES.between(issuedAt, expiresAt)
        assertThat(diffMinutes).isEqualTo(15)
    }

    @Test
    fun `access token includes username claim`() {
        val token = tokenService.createAccessToken("alice", "user-123", listOf("ROLE_USER"))

        val jwt = jwtDecoder.decode(token)

        assertThat(jwt.getClaimAsString("username")).isEqualTo("alice")
    }

    @Test
    fun `access token includes roles claim`() {
        val roles = listOf("ROLE_USER", "SERVICE_VAULT")
        val token = tokenService.createAccessToken("alice", "user-123", roles)

        val jwt = jwtDecoder.decode(token)

        assertThat(jwt.getClaimAsStringList("roles")).containsExactlyElementsOf(roles)
    }

    @Test
    fun `access token subject matches userId`() {
        val token = tokenService.createAccessToken("alice", "user-123", listOf("ROLE_USER"))

        val jwt = jwtDecoder.decode(token)

        assertThat(jwt.subject).isEqualTo("user-123")
    }

    @Test
    fun `refresh token has type=refresh claim`() {
        val token = tokenService.createRefreshToken("user-123")

        val jwt = jwtDecoder.decode(token)

        assertThat(jwt.getClaimAsString("type")).isEqualTo("refresh")
    }

    @Test
    fun `refresh token has 7-day expiry`() {
        val token = tokenService.createRefreshToken("user-123")

        val jwt = jwtDecoder.decode(token)
        val expiresAt = jwt.expiresAt!!
        val issuedAt = jwt.issuedAt!!

        val diffDays = ChronoUnit.DAYS.between(issuedAt, expiresAt)
        assertThat(diffDays).isEqualTo(7)
    }

    @Test
    fun `TOTP challenge token has type=totp_challenge claim`() {
        val token = tokenService.createTotpChallengeToken("user-123", "alice")

        val jwt = jwtDecoder.decode(token)

        assertThat(jwt.getClaimAsString("type")).isEqualTo("totp_challenge")
        assertThat(jwt.getClaimAsString("username")).isEqualTo("alice")
    }

    @Test
    fun `TOTP challenge token has 5-minute expiry`() {
        val token = tokenService.createTotpChallengeToken("user-123", "alice")

        val jwt = jwtDecoder.decode(token)
        val expiresAt = jwt.expiresAt!!
        val issuedAt = jwt.issuedAt!!

        val diffMinutes = ChronoUnit.MINUTES.between(issuedAt, expiresAt)
        assertThat(diffMinutes).isEqualTo(5)
    }
}
