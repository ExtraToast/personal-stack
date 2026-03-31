package com.jorisjonkers.personalstack.auth.config

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.UUID

@Configuration
class JwtConfig(
    @param:Value("\${auth.signing-key:}")
    private val signingKeyPem: String,
    @param:Value("\${auth.signing-key-previous:}")
    private val previousSigningKeyPem: String,
) {
    /**
     * Loads a shared RSA key from PEM (Vault KV in production) if available,
     * otherwise generates an ephemeral key pair (suitable for single-replica dev).
     *
     * When a previous signing key is present (during key rotation), both keys are
     * included in the JWKSet. The current key is used for signing new tokens, while
     * the previous key remains available for verifying tokens issued before rotation.
     */
    @Bean
    fun jwkSource(): JWKSource<SecurityContext> {
        val keys = mutableListOf<RSAKey>()

        if (signingKeyPem.isNotBlank()) {
            val privateKey = parseRsaPrivateKey(signingKeyPem)
            val publicKey = derivePublicKey(privateKey)
            keys.add(
                RSAKey
                    .Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(CURRENT_KEY_ID)
                    .build(),
            )
        } else {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_KEY_SIZE) }.generateKeyPair()
            keys.add(
                RSAKey
                    .Builder(keyPair.public as RSAPublicKey)
                    .privateKey(keyPair.private as RSAPrivateKey)
                    .keyID(UUID.randomUUID().toString())
                    .build(),
            )
        }

        // Include the previous key for verification during rotation window
        if (previousSigningKeyPem.isNotBlank()) {
            val prevPrivateKey = parseRsaPrivateKey(previousSigningKeyPem)
            val prevPublicKey = derivePublicKey(prevPrivateKey)
            keys.add(
                RSAKey
                    .Builder(prevPublicKey)
                    .privateKey(prevPrivateKey)
                    .keyID(PREVIOUS_KEY_ID)
                    .build(),
            )
        }

        return ImmutableJWKSet(JWKSet(keys.toList()))
    }

    @Bean
    fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder {
        val jwtProcessor =
            DefaultJWTProcessor<SecurityContext>().apply {
                jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
            }
        return NimbusJwtDecoder(jwtProcessor)
    }

    @Bean
    fun jwtEncoder(jwkSource: JWKSource<SecurityContext>): JwtEncoder = NimbusJwtEncoder(jwkSource)

    companion object {
        private const val RSA_KEY_SIZE = 2048
        private const val CURRENT_KEY_ID = "signing-key-current"
        private const val PREVIOUS_KEY_ID = "signing-key-previous"
        private val RSA_PUBLIC_EXPONENT = java.math.BigInteger.valueOf(65537)

        private fun parseRsaPrivateKey(pem: String): RSAPrivateKey {
            val base64 =
                pem
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\s".toRegex(), "")
            val keyBytes = Base64.getDecoder().decode(base64)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec) as RSAPrivateKey
        }

        private fun derivePublicKey(privateKey: RSAPrivateKey): RSAPublicKey {
            val publicKeySpec = RSAPublicKeySpec(privateKey.modulus, RSA_PUBLIC_EXPONENT)
            return KeyFactory.getInstance("RSA").generatePublic(publicKeySpec) as RSAPublicKey
        }
    }
}
