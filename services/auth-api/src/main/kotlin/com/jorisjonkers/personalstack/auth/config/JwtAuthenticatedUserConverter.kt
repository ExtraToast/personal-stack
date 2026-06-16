package com.jorisjonkers.personalstack.auth.config

import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

class JwtAuthenticatedUserConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val userId =
            runCatching { UserId(UUID.fromString(jwt.subject)) }.getOrElse {
                throw BadCredentialsException("JWT subject must be a user UUID", it)
            }
        val username = jwt.getClaimAsString("username") ?: jwt.getClaimAsString("preferred_username") ?: jwt.subject
        val roles = jwt.getClaimAsStringList("roles") ?: emptyList()
        val user =
            AuthenticatedUser.of(
                userId = userId,
                username = username,
                roles = roles,
            )
        return UsernamePasswordAuthenticationToken(user, jwt, user.authorities)
    }
}
