package com.jorisjonkers.privatestack.common.web

import com.jorisjonkers.privatestack.common.exception.DomainException
import com.jorisjonkers.privatestack.common.exception.NotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI
import org.slf4j.LoggerFactory

@RestControllerAdvice
open class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail(
            type = URI.create("https://jorisjonkers.dev/errors/not-found"),
            title = "Resource Not Found",
            status = HttpStatus.NOT_FOUND.value(),
            detail = ex.message,
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
    }

    @ExceptionHandler(DomainException::class)
    fun handleDomain(ex: DomainException): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail(
            type = URI.create("https://jorisjonkers.dev/errors/${ex.code.lowercase().replace('_', '-')}"),
            title = ex.code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercaseChar() },
            status = HttpStatus.BAD_REQUEST.value(),
            detail = ex.message,
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val fieldErrors = ex.bindingResult.fieldErrors.map { error ->
            FieldError(
                field = error.field,
                message = error.defaultMessage ?: "Invalid value",
            )
        }
        val body = ProblemDetail(
            type = URI.create("https://jorisjonkers.dev/errors/validation-error"),
            title = "Validation Error",
            status = HttpStatus.UNPROCESSABLE_ENTITY.value(),
            detail = "One or more fields failed validation",
            errors = fieldErrors,
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ProblemDetail> {
        log.error("Unhandled exception", ex)
        val body = ProblemDetail(
            type = URI.create("https://jorisjonkers.dev/errors/internal-error"),
            title = "Internal Server Error",
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            detail = "An unexpected error occurred",
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }
}
