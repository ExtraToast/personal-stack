package com.jorisjonkers.personalstack.auth.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.session.SessionRepositoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.data.redis.RedisIndexedSessionRepository
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession
import org.springframework.session.web.http.CookieHttpSessionIdResolver
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer
import org.springframework.session.web.http.HttpSessionIdResolver
import java.time.Duration

@Configuration
@EnableRedisHttpSession(redisNamespace = "auth-api")
class SessionConfig(
    @param:Value("\${session.cookie.domain:}")
    private val cookieDomain: String,
    @param:Value("\${session.cookie.secure:true}")
    private val secureCookie: Boolean,
    @param:Value("\${session.cookie.same-site:None}")
    private val sameSite: String,
    @param:Value("\${session.timeout:30d}")
    private val sessionTimeout: Duration,
) {
    @Bean
    fun cookieSerializer(): CookieSerializer =
        DefaultCookieSerializer().apply {
            setCookieName("SESSION")
            setCookiePath("/")
            setSameSite(sameSite)
            setUseHttpOnlyCookie(true)
            setCookieMaxAge(sessionTimeout.seconds.toInt())
            if (cookieDomain.isNotBlank()) {
                setDomainName(cookieDomain)
            }
            setUseSecureCookie(secureCookie)
        }

    @Bean
    fun httpSessionIdResolver(): HttpSessionIdResolver =
        CookieHttpSessionIdResolver().apply {
            setCookieSerializer(cookieSerializer())
        }

    @Bean
    fun redisSessionRepositoryCustomizer(): SessionRepositoryCustomizer<RedisIndexedSessionRepository> =
        SessionRepositoryCustomizer { sessionRepository ->
            sessionRepository.setDefaultMaxInactiveInterval(sessionTimeout)
        }
}
