package com.jorisjonkers.personalstack.auth.infrastructure.security

import com.jorisjonkers.personalstack.auth.domain.port.PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BcryptPasswordEncoderAdapter : PasswordEncoder {
    private val bcrypt = BCryptPasswordEncoder()

    override fun encode(rawPassword: String): String = bcrypt.encode(rawPassword)!!

    override fun matches(
        rawPassword: String,
        encodedPassword: String,
    ): Boolean = bcrypt.matches(rawPassword, encodedPassword)
}
