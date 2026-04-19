package com.jorisjonkers.personalstack.auth.infrastructure.web

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class LogoutController(
    @param:Value("\${session.cookie.domain:}")
    private val cookieDomain: String,
) {
    @GetMapping("/logout")
    fun logoutRedirect(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        clearSession(request, response)
        response.sendRedirect(if (cookieDomain.isNotBlank()) "https://$cookieDomain" else "/")
    }

    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        clearSession(request, response)
        return ResponseEntity.noContent().build()
    }

    private fun clearSession(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        request.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        response.addCookie(expiredCookie(name = "SESSION", secure = true, httpOnly = true))
        response.addCookie(expiredCookie(name = "XSRF-TOKEN", secure = false, httpOnly = false))
    }

    private fun expiredCookie(
        name: String,
        secure: Boolean,
        httpOnly: Boolean,
    ): Cookie =
        Cookie(name, "").apply {
            maxAge = 0
            path = "/"
            this.secure = secure
            this.isHttpOnly = httpOnly
            if (cookieDomain.isNotBlank()) {
                domain = cookieDomain
            }
            if (name == "SESSION") {
                setAttribute("SameSite", "Lax")
            }
        }
}
