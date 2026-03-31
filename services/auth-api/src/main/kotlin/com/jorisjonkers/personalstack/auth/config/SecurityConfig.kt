package com.jorisjonkers.personalstack.auth.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URLEncoder

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    @param:Value("\${auth.login-url:http://localhost:5174/login}")
    private val loginUrl: String,
    @param:Value("\${auth.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://localhost:5175}")
    private val corsAllowedOrigins: String,
    @param:Value("\${session.cookie.domain:}")
    private val cookieDomain: String,
) {
    @Bean
    @Order(2)
    fun forwardAuthSecurityFilterChain(
        http: HttpSecurity,
        corsConfigurationSource: CorsConfigurationSource,
    ): SecurityFilterChain {
        http
            .securityMatcher("/api/v1/auth/verify")
            .cors { it.configurationSource(corsConfigurationSource) }
            .csrf { it.disable() }
            .securityContext { it.securityContextRepository(HttpSessionSecurityContextRepository()) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .exceptionHandling { it.authenticationEntryPoint(forwardAuthEntryPoint()) }
        return http.build()
    }

    @Bean
    @Order(3)
    fun applicationSecurityFilterChain(
        http: HttpSecurity,
        corsConfigurationSource: CorsConfigurationSource,
    ): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource) }
            .securityContext { it.securityContextRepository(HttpSessionSecurityContextRepository()) }
            .csrf { configureCsrf(it) }
            .addFilterAfter(CsrfCookieFilter(), CsrfFilter::class.java)
            .authorizeHttpRequests { configureAuthorization(it) }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
        return http.build()
    }

    private fun configureCsrf(
        csrf: org.springframework.security.config.annotation.web.configurers
            .CsrfConfigurer<HttpSecurity>,
    ) {
        csrf.csrfTokenRepository(
            CookieCsrfTokenRepository.withHttpOnlyFalse().apply {
                setCookieCustomizer { builder ->
                    if (cookieDomain.isNotBlank()) {
                        builder.domain(cookieDomain)
                    }
                }
            },
        )
        csrf.csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
        csrf.ignoringRequestMatchers(*(PUBLIC_POST_ENDPOINTS + CSRF_FREE_ENDPOINTS))
    }

    private fun configureAuthorization(
        auth: org.springframework.security.config.annotation.web.configurers
            .AuthorizeHttpRequestsConfigurer<HttpSecurity>
            .AuthorizationManagerRequestMatcherRegistry,
    ) {
        auth
            .requestMatchers(*PUBLIC_ENDPOINTS)
            .permitAll()
            .anyRequest()
            .authenticated()
    }

    private fun forwardAuthEntryPoint() =
        AuthenticationEntryPoint {
            request: HttpServletRequest,
            response: HttpServletResponse,
            _: AuthenticationException,
            ->
            val proto = request.getHeader("X-Forwarded-Proto") ?: "https"
            val host = request.getHeader("X-Forwarded-Host") ?: request.serverName
            val uri = request.getHeader("X-Forwarded-Uri") ?: "/"
            val originalUrl = "$proto://$host$uri"
            val redirectUrl = "$loginUrl?redirect=${URLEncoder.encode(originalUrl, Charsets.UTF_8)}"
            response.sendRedirect(redirectUrl)
        }

    @Bean
    fun passwordEncoder(): PasswordEncoder =
        (PasswordEncoderFactories.createDelegatingPasswordEncoder() as DelegatingPasswordEncoder).apply {
            // User passwords are stored as raw BCrypt hashes without an "{id}" prefix,
            // while OAuth clients use prefixed secrets such as "{noop}n8n-secret".
            setDefaultPasswordEncoderForMatches(BCryptPasswordEncoder())
        }

    @Bean
    fun authenticationManager(
        userDetailsService: UserDetailsService,
        passwordEncoder: PasswordEncoder,
    ): AuthenticationManager {
        val provider =
            DaoAuthenticationProvider(userDetailsService).apply {
                setPasswordEncoder(passwordEncoder)
            }
        return ProviderManager(provider)
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins = corsAllowedOrigins.split(",").map { it.trim() }
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    companion object {
        private val PUBLIC_POST_ENDPOINTS =
            arrayOf(
                "/api/v1/auth/session-login",
                "/api/v1/users/register",
                "/api/v1/auth/login",
                "/api/v1/auth/totp-challenge",
                "/api/v1/auth/refresh",
                "/api/v1/auth/resend-confirmation",
            )

        private val CSRF_FREE_ENDPOINTS =
            arrayOf(
                "/api/actuator/**",
                "/api/v1/health",
                "/api/v1/auth/verify",
            )

        private val PUBLIC_ENDPOINTS =
            arrayOf(
                "/api/actuator/**",
                "/api/v1/health",
                "/api/v1/api-docs/**",
                "/api/v1/swagger-ui/**",
                "/api/v1/users/register",
                "/api/v1/auth/login",
                "/api/v1/auth/totp-challenge",
                "/api/v1/auth/refresh",
                "/api/v1/auth/confirm-email",
                "/api/v1/auth/resend-confirmation",
                "/api/v1/auth/session-login",
            )
    }

    private class CsrfCookieFilter : OncePerRequestFilter() {
        override fun shouldNotFilter(request: HttpServletRequest): Boolean {
            val path = request.requestURI
            return path == "/api/v1/auth/verify" ||
                path == "/api/v1/health" ||
                path.startsWith("/api/actuator/")
        }

        override fun doFilterInternal(
            request: HttpServletRequest,
            response: HttpServletResponse,
            filterChain: jakarta.servlet.FilterChain,
        ) {
            (request.getAttribute(CsrfToken::class.java.name) as CsrfToken?)?.token
            filterChain.doFilter(request, response)
        }
    }
}
