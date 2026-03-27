package com.jorisjonkers.personalstack.auth.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.net.URLEncoder

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    @Value("\${auth.login-url:http://localhost:5174/login}")
    private val loginUrl: String,
) {
    /**
     * Forward-auth filter chain (order 2).
     * The /api/v1/auth/verify endpoint redirects to the login page when unauthenticated,
     * so Traefik's forwardAuth middleware delivers a 302 to the browser instead of a raw 401.
     */
    @Bean
    @Order(2)
    fun forwardAuthSecurityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
    ): SecurityFilterChain {
        http
            .securityMatcher("/api/v1/auth/verify")
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { rs ->
                rs.jwt { jwt -> jwt.decoder(jwtDecoder) }
                rs.authenticationEntryPoint(forwardAuthEntryPoint())
            }
        return http.build()
    }

    /**
     * Resource server filter chain (order 3).
     * Protects the REST API endpoints with JWT bearer tokens.
     * Auth server endpoints (order 1) are handled by [AuthorizationServerConfig].
     */
    @Bean
    @Order(3)
    fun resourceServerSecurityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
    ): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/api/v1/api-docs/**",
                        "/api/v1/swagger-ui/**",
                        "/api/v1/users/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                    ).permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { rs ->
                rs.jwt { jwt -> jwt.decoder(jwtDecoder) }
            }
        return http.build()
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
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

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

    private fun corsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins =
                    listOf(
                        "https://auth.jorisjonkers.dev",
                        "https://app.jorisjonkers.dev",
                        "http://localhost:5174",
                        "http://localhost:5175",
                    )
                allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
