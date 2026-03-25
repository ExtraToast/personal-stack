package com.jorisjonkers.privatestack.auth.domain.service

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.apache.commons.codec.binary.Base32
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class TotpService {

    private val config = TimeBasedOneTimePasswordConfig(
        codeDigits = 6,
        hmacAlgorithm = HmacAlgorithm.SHA1,
        timeStep = 30,
        timeStepUnit = TimeUnit.SECONDS,
    )

    fun generateSecret(): String {
        val bytes = ByteArray(20).also { SecureRandom().nextBytes(it) }
        return Base32().encodeToString(bytes).trimEnd('=')
    }

    fun generateQrUri(secret: String, username: String, issuer: String = "jorisjonkers.dev"): String {
        val encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8)
        val encodedAccount = URLEncoder.encode("$issuer:$username", StandardCharsets.UTF_8)
        return "otpauth://totp/$encodedAccount?secret=$secret&issuer=$encodedIssuer&algorithm=SHA1&digits=6&period=30"
    }

    fun verifyCode(secret: String, code: String): Boolean {
        val secretBytes = Base32().decode(secret.padEnd((secret.length + 7) / 8 * 8, '='))
        val generator = TimeBasedOneTimePasswordGenerator(secretBytes, config)
        return generator.isValid(code)
    }
}
