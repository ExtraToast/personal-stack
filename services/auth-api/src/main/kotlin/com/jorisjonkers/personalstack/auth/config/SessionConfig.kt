package com.jorisjonkers.personalstack.auth.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer

@Configuration
@EnableRedisHttpSession(redisNamespace = "auth-api")
class SessionConfig(
    @param:Value("\${session.cookie.domain:}")
    private val cookieDomain: String,
    @param:Value("\${session.cookie.secure:false}")
    private val secureCookie: Boolean,
) {
    @Bean
    fun cookieSerializer(): CookieSerializer =
        DefaultCookieSerializer().apply {
            setCookieName("SESSION")
            setCookiePath("/")
            setSameSite("Lax")
            setUseHttpOnlyCookie(true)
            if (cookieDomain.isNotBlank()) {
                setDomainName(cookieDomain)
            }
            setUseSecureCookie(secureCookie)
        }
}
