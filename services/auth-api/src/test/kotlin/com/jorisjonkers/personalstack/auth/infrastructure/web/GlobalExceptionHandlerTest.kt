package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.common.exception.DomainException
import com.jorisjonkers.personalstack.common.exception.NotFoundException
import com.jorisjonkers.personalstack.common.web.GlobalExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.web.bind.MethodArgumentNotValidException
import java.net.URI

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `handleNotFound returns 404 ProblemDetail`() {
        val ex = NotFoundException("User", "abc-123")

        val response = handler.handleNotFound(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        val body = response.body!!
        assertThat(body.status).isEqualTo(404)
        assertThat(body.title).isEqualTo("Resource Not Found")
        assertThat(body.detail).isEqualTo("User not found: abc-123")
        assertThat(body.type).isEqualTo(URI.create("https://jorisjonkers.dev/errors/not-found"))
    }

    @Test
    fun `handleDomain returns 400 ProblemDetail with code`() {
        val ex = DomainException("Username already exists", "USERNAME_TAKEN")

        val response = handler.handleDomain(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val body = response.body!!
        assertThat(body.status).isEqualTo(400)
        assertThat(body.title).isEqualTo("Username taken")
        assertThat(body.detail).isEqualTo("Username already exists")
        assertThat(body.type).isEqualTo(URI.create("https://jorisjonkers.dev/errors/username-taken"))
    }

    @Test
    fun `handleValidation returns 422 with field errors`() {
        val bindingResult = BeanPropertyBindingResult(ValidationTarget(), "request")
        bindingResult.rejectValue("username", "NotBlank", "Username is required")

        // Use any public method to satisfy MethodParameter - toString is always available
        val methodParameter =
            MethodParameter(
                Any::class.java.getMethod("toString"),
                -1,
            )
        val ex = MethodArgumentNotValidException(methodParameter, bindingResult)

        val response = handler.handleValidation(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        val body = response.body!!
        assertThat(body.status).isEqualTo(422)
        assertThat(body.title).isEqualTo("Validation Error")
        assertThat(body.detail).isEqualTo("One or more fields failed validation")
        assertThat(body.errors).hasSize(1)
        assertThat(body.errors[0].field).isEqualTo("username")
        assertThat(body.errors[0].message).isEqualTo("Username is required")
    }

    @Test
    fun `handleUnexpected returns 500 ProblemDetail`() {
        val ex = RuntimeException("Something broke")

        val response = handler.handleUnexpected(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        val body = response.body!!
        assertThat(body.status).isEqualTo(500)
        assertThat(body.title).isEqualTo("Internal Server Error")
        assertThat(body.detail).isEqualTo("An unexpected error occurred")
        assertThat(body.type).isEqualTo(URI.create("https://jorisjonkers.dev/errors/internal-error"))
    }

    @Test
    fun `ProblemDetail has correct RFC 7807 structure`() {
        val ex = DomainException("Email not confirmed", "EMAIL_NOT_CONFIRMED")

        val response = handler.handleDomain(ex)
        val body = response.body!!

        // RFC 7807 requires type, title, status; detail and instance are optional
        assertThat(body.type).isNotNull
        assertThat(body.title).isNotBlank
        assertThat(body.status).isBetween(100, 599)
        assertThat(body.detail).isNotNull
        // Errors list should be empty for non-validation errors
        assertThat(body.errors).isEmpty()
    }

    /** Simple JavaBean for creating [BeanPropertyBindingResult] with a valid property. */
    @Suppress("unused")
    class ValidationTarget {
        var username: String = ""
    }
}
