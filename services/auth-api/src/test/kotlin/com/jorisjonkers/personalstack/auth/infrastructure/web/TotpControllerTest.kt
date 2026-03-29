package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.application.command.EnrollTotpCommand
import com.jorisjonkers.personalstack.auth.application.command.EnrollTotpCommandHandler
import com.jorisjonkers.personalstack.auth.application.command.InvalidTotpCodeException
import com.jorisjonkers.personalstack.auth.application.command.InvalidTotpStateException
import com.jorisjonkers.personalstack.auth.application.command.TotpAlreadyEnrolledException
import com.jorisjonkers.personalstack.auth.application.command.VerifyTotpCommand
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.TotpVerifyRequest
import com.jorisjonkers.personalstack.common.command.CommandBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import java.util.UUID

class TotpControllerTest {
    private val enrollTotpCommandHandler = mockk<EnrollTotpCommandHandler>()
    private val commandBus = mockk<CommandBus>(relaxUnitFun = true)
    private val totpService = mockk<TotpService>()

    private val controller = TotpController(enrollTotpCommandHandler, commandBus, totpService)

    private val userId = UUID.randomUUID()

    private fun buildUser(
        id: UUID = userId,
        username: String = "alice",
    ): AuthenticatedUser =
        AuthenticatedUser(
            userId = UserId(id),
            username = username,
            roles = listOf("ROLE_USER"),
        )

    @Test
    fun `enroll returns secret and qrUri`() {
        val jwt = buildUser()
        val secret = "JBSWY3DPEHPK3PXP"
        val qrUri = "otpauth://totp/jorisjonkers.dev%3Aalice?secret=$secret"

        every { enrollTotpCommandHandler.handle(EnrollTotpCommand(UserId(userId))) } returns secret
        every { totpService.generateQrUri(secret, "alice") } returns qrUri

        val response = controller.enroll(jwt)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.secret).isEqualTo(secret)
        assertThat(response.body!!.qrUri).isEqualTo(qrUri)
    }

    @Test
    fun `enroll with already enrolled user throws TotpAlreadyEnrolledException`() {
        val jwt = buildUser()

        every {
            enrollTotpCommandHandler.handle(EnrollTotpCommand(UserId(userId)))
        } throws TotpAlreadyEnrolledException()

        assertThatThrownBy {
            controller.enroll(jwt)
        }.isInstanceOf(TotpAlreadyEnrolledException::class.java)
    }

    @Test
    fun `verify with valid code returns 204`() {
        val jwt = buildUser()
        val request = TotpVerifyRequest(code = "123456")

        val response = controller.verify(jwt, request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)

        val commandSlot = slot<VerifyTotpCommand>()
        verify { commandBus.dispatch(capture(commandSlot)) }
        assertThat(commandSlot.captured.userId).isEqualTo(UserId(userId))
        assertThat(commandSlot.captured.code).isEqualTo("123456")
    }

    @Test
    fun `verify with invalid code throws InvalidTotpCodeException`() {
        val jwt = buildUser()
        val request = TotpVerifyRequest(code = "000000")

        every {
            commandBus.dispatch(any<VerifyTotpCommand>())
        } throws InvalidTotpCodeException()

        assertThatThrownBy {
            controller.verify(jwt, request)
        }.isInstanceOf(InvalidTotpCodeException::class.java)
    }

    @Test
    fun `verify with non-6-digit code is rejected by validation annotation`() {
        // TotpVerifyRequest has @Pattern(regexp = "^\\d{6}$") on code field.
        // In a real Spring MVC context, "abc" would be rejected before reaching
        // the controller. Here we verify the annotation is present.
        val annotations =
            TotpVerifyRequest::class.java
                .getDeclaredField("code")
                .annotations
        val hasPatternAnnotation = annotations.any { it.annotationClass.simpleName == "Pattern" }
        assertThat(hasPatternAnnotation).isTrue()
    }

    @Test
    fun `verify without TOTP enrolled throws InvalidTotpStateException`() {
        val jwt = buildUser()
        val request = TotpVerifyRequest(code = "123456")

        every {
            commandBus.dispatch(any<VerifyTotpCommand>())
        } throws InvalidTotpStateException("TOTP enrollment not started — call enroll first")

        assertThatThrownBy {
            controller.verify(jwt, request)
        }.isInstanceOf(InvalidTotpStateException::class.java)
    }
}
