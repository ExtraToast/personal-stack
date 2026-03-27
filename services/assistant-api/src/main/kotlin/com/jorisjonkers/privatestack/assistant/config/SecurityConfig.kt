package com.jorisjonkers.privatestack.assistant.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
class SecurityConfig {
    @Bean
    fun xUserIdFilterRegistration(): FilterRegistrationBean<XUserIdFilter> =
        FilterRegistrationBean(XUserIdFilter()).apply {
            addUrlPatterns("/api/*")
            order = 1
        }
}

class XUserIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val userId = request.getHeader("X-User-Id")
        if (userId.isNullOrBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-User-Id header")
            return
        }
        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return path.startsWith("/actuator") ||
            path.startsWith("/api/v1/api-docs") ||
            path.startsWith("/api/v1/swagger-ui")
    }
}
