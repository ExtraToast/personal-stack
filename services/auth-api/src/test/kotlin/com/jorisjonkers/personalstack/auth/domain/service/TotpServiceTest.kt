package com.jorisjonkers.personalstack.auth.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TotpServiceTest {
    private val totpService = TotpService()

    @Test
    fun `generateSecret returns a base32 string of expected length`() {
        val secret = totpService.generateSecret()
        assertThat(secret).isNotBlank()
        assertThat(secret).matches("[A-Z2-7]+")
        assertThat(secret.length).isGreaterThanOrEqualTo(16)
    }

    @Test
    fun `generateQrUri returns valid otpauth URI`() {
        val secret = totpService.generateSecret()
        val uri = totpService.generateQrUri(secret, "alice")
        assertThat(uri).startsWith("otpauth://totp/")
        assertThat(uri).contains("secret=$secret")
        assertThat(uri).contains("algorithm=SHA1")
        assertThat(uri).contains("digits=6")
        assertThat(uri).contains("period=30")
    }

    @Test
    fun `generateSecret produces unique secrets on each call`() {
        val secrets = (1..10).map { totpService.generateSecret() }.toSet()
        assertThat(secrets).hasSize(10)
    }

    @Test
    fun `verifyCode returns false for incorrect code`() {
        val secret = totpService.generateSecret()
        assertThat(totpService.verifyCode(secret, "000000")).isFalse()
    }

    @Test
    fun `generateSecret returns valid base32 string`() {
        val secret = totpService.generateSecret()
        assertThat(secret).matches("[A-Z2-7]+")
    }

    @Test
    fun `generateSecret produces unique values`() {
        val secrets = (1..20).map { totpService.generateSecret() }.toSet()
        assertThat(secrets).hasSize(20)
    }

    @Test
    fun `verifyCode rejects empty string`() {
        val secret = totpService.generateSecret()
        assertThat(totpService.verifyCode(secret, "")).isFalse()
    }

    @Test
    fun `generateQrUri contains otpauth scheme`() {
        val secret = totpService.generateSecret()
        val uri = totpService.generateQrUri(secret, "bob")
        assertThat(uri).startsWith("otpauth://")
    }

    @Test
    fun `generateQrUri encodes username in URI`() {
        val secret = totpService.generateSecret()
        val uri = totpService.generateQrUri(secret, "bob")
        assertThat(uri).contains("bob")
    }
}
