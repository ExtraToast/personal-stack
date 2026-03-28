package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.common.web.ProblemDetail
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthExceptionHandler {
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ProblemDetail> {
        val body =
            ProblemDetail(
                type = URI.create("https://jorisjonkers.dev/errors/forbidden"),
                title = "Forbidden",
                status = HttpStatus.FORBIDDEN.value(),
                detail = "You do not have permission to perform this action",
            )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body)
    }
}
