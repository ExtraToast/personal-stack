package com.jorisjonkers.personalstack.common.web

import com.jorisjonkers.personalstack.common.exception.DomainException
import com.jorisjonkers.personalstack.common.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.validation.BindingResult
import org.springframework.web.bind.MethodArgumentNotValidException
import java.net.URI
import org.springframework.validation.FieldError as SpringFieldError

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `handleNotFound returns 404 with correct ProblemDetail`() {
        val ex = NotFoundException("User", "123")

        val response = handler.handleNotFound(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        val body = response.body!!
        assertThat(body.status).isEqualTo(404)
        assertThat(body.title).isEqualTo("Resource Not Found")
        assertThat(body.detail).isEqualTo("User not found: 123")
        assertThat(body.type).isEqualTo(URI.create("https://jorisjonkers.dev/errors/not-found"))
    }

    @Test
    fun `handleDomain returns 400 with code-based type and title`() {
        val ex = DomainException("Email already taken", "EMAIL_TAKEN")

        val response = handler.handleDomain(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val body = response.body!!
        assertThat(body.status).isEqualTo(400)
        assertThat(body.title).isEqualTo("Email taken")
        assertThat(body.detail).isEqualTo("Email already taken")
        assertThat(body.type).isEqualTo(URI.create("https://jorisjonkers.dev/errors/email-taken"))
    }

    @Test
    fun `handleDomain handles single word code`() {
        val ex = DomainException("Something failed", "FORBIDDEN")

        val response = handler.handleDomain(ex)

        val body = response.body!!
        assertThat(body.title).isEqualTo("Forbidden")
        assertThat(body.type).isEqualTo(URI.create("https://jorisjonkers.dev/errors/forbidden"))
    }

    @Test
    fun `handleValidation returns 422 with field errors`() {
        val fieldError1 = SpringFieldError("obj", "email", "must not be blank")
        val fieldError2 = SpringFieldError("obj", "name", null, false, null, null, null)

        val bindingResult = mockk<BindingResult>()
        every { bindingResult.fieldErrors } returns listOf(fieldError1, fieldError2)

        val ex = mockk<MethodArgumentNotValidException>()
        every { ex.bindingResult } returns bindingResult

        val response = handler.handleValidation(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        val body = response.body!!
        assertThat(body.status).isEqualTo(422)
        assertThat(body.title).isEqualTo("Validation Error")
        assertThat(body.detail).isEqualTo("One or more fields failed validation")
        assertThat(body.errors).hasSize(2)
        assertThat(body.errors[0].field).isEqualTo("email")
        assertThat(body.errors[0].message).isEqualTo("must not be blank")
        assertThat(body.errors[1].field).isEqualTo("name")
        assertThat(body.errors[1].message).isEqualTo("Invalid value")
    }

    @Test
    fun `handleUnexpected returns 500`() {
        val ex = RuntimeException("boom")

        val response = handler.handleUnexpected(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        val body = response.body!!
        assertThat(body.status).isEqualTo(500)
        assertThat(body.title).isEqualTo("Internal Server Error")
        assertThat(body.detail).isEqualTo("An unexpected error occurred")
        assertThat(body.type).isEqualTo(URI.create("https://jorisjonkers.dev/errors/internal-error"))
    }

    @Test
    fun `ProblemDetail data class properties and defaults`() {
        val pd = ProblemDetail(title = "Test", status = 200)
        assertThat(pd.type).isEqualTo(URI.create("about:blank"))
        assertThat(pd.detail).isNull()
        assertThat(pd.instance).isNull()
        assertThat(pd.errors).isEmpty()
    }

    @Test
    fun `ProblemDetail with all fields`() {
        val pd =
            ProblemDetail(
                type = URI.create("https://example.com/error"),
                title = "Error",
                status = 400,
                detail = "Something went wrong",
                instance = URI.create("/api/test"),
                errors = listOf(FieldError("f1", "m1")),
            )
        assertThat(pd.type).isEqualTo(URI.create("https://example.com/error"))
        assertThat(pd.title).isEqualTo("Error")
        assertThat(pd.status).isEqualTo(400)
        assertThat(pd.detail).isEqualTo("Something went wrong")
        assertThat(pd.instance).isEqualTo(URI.create("/api/test"))
        assertThat(pd.errors).hasSize(1)
        assertThat(pd.errors[0].field).isEqualTo("f1")
    }

    @Test
    fun `ProblemDetail equals and hashCode`() {
        val pd1 = ProblemDetail(title = "T", status = 200)
        val pd2 = ProblemDetail(title = "T", status = 200)
        assertThat(pd1).isEqualTo(pd2)
        assertThat(pd1.hashCode()).isEqualTo(pd2.hashCode())
    }

    @Test
    fun `FieldError data class`() {
        val fe = FieldError(field = "email", message = "required")
        assertThat(fe.field).isEqualTo("email")
        assertThat(fe.message).isEqualTo("required")
        val fe2 = fe.copy(message = "invalid")
        assertThat(fe2.message).isEqualTo("invalid")
    }
}
