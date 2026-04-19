package com.jorisjonkers.personalstack.auth.domain.service

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.apache.commons.codec.binary.Base32
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class TotpService {
    private val config =
        TimeBasedOneTimePasswordConfig(
            codeDigits = TOTP_CODE_DIGITS,
            hmacAlgorithm = HmacAlgorithm.SHA1,
            timeStep = TOTP_TIME_STEP,
            timeStepUnit = TimeUnit.SECONDS,
        )

    fun generateSecret(): String {
        val bytes = ByteArray(SECRET_BYTE_LENGTH).also { SecureRandom().nextBytes(it) }
        return Base32().encodeToString(bytes).trimEnd('=')
    }

    fun generateQrUri(
        secret: String,
        username: String,
        issuer: String = "jorisjonkers.dev",
    ): String {
        val encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8)
        val encodedAccount = URLEncoder.encode("$issuer:$username", StandardCharsets.UTF_8)
        return "otpauth://totp/$encodedAccount?secret=$secret&issuer=$encodedIssuer" +
            "&algorithm=SHA1&digits=$TOTP_CODE_DIGITS&period=$TOTP_TIME_STEP"
    }

    fun verifyCode(
        secret: String,
        code: String,
    ): Boolean {
        val padded =
            secret.padEnd(
                (secret.length + BASE32_GROUP_SIZE - 1) / BASE32_GROUP_SIZE * BASE32_GROUP_SIZE,
                '=',
            )
        val secretBytes = Base32().decode(padded)
        val generator = TimeBasedOneTimePasswordGenerator(secretBytes, config)
        return generator.isValid(code)
    }

    companion object {
        private const val SECRET_BYTE_LENGTH = 20
        private const val TOTP_CODE_DIGITS = 6
        private const val TOTP_TIME_STEP = 30L
        private const val BASE32_GROUP_SIZE = 8
    }
}
