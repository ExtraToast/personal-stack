package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.application.command.ConfirmEmailCommand
import com.jorisjonkers.personalstack.auth.domain.exception.InvalidConfirmationTokenException
import com.jorisjonkers.personalstack.common.command.CommandBus
import com.jorisjonkers.personalstack.common.web.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class EmailConfirmationControllerTest {
    private val commandBus = mockk<CommandBus>(relaxUnitFun = true)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    EmailConfirmationController(commandBus),
                ).setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `confirm email with valid token returns 200`() {
        mockMvc
            .get("/api/v1/auth/confirm-email") {
                param("token", "valid-token-123")
            }.andExpect {
                status { isOk() }
                jsonPath("$.message") { value("Email confirmed successfully") }
            }

        verify { commandBus.dispatch(ConfirmEmailCommand("valid-token-123")) }
    }

    @Test
    fun `confirm email with expired token returns 400`() {
        every {
            commandBus.dispatch(ConfirmEmailCommand("expired-token"))
        } throws InvalidConfirmationTokenException("Confirmation token has expired")

        mockMvc
            .get("/api/v1/auth/confirm-email") {
                param("token", "expired-token")
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `confirm email with used token returns 400`() {
        every {
            commandBus.dispatch(ConfirmEmailCommand("used-token"))
        } throws InvalidConfirmationTokenException("Confirmation token has already been used")

        mockMvc
            .get("/api/v1/auth/confirm-email") {
                param("token", "used-token")
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `confirm email without token returns error`() {
        mockMvc
            .get("/api/v1/auth/confirm-email")
            .andExpect {
                status { isInternalServerError() }
            }
    }
}
