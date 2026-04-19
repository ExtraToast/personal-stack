package com.jorisjonkers.personalstack.auth.infrastructure.email

import com.jorisjonkers.personalstack.auth.domain.event.PasswordResetRequestedEvent
import com.jorisjonkers.personalstack.common.email.EmailRequest
import com.jorisjonkers.personalstack.common.email.EmailService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.Optional

@Component
class PasswordResetEmailListener(
    private val emailService: Optional<EmailService>,
    @param:Value("\${app.password-reset-url:http://localhost:5174/reset-password}")
    private val passwordResetBaseUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onPasswordResetRequested(event: PasswordResetRequestedEvent) {
        val service = emailService.orElse(null)
        if (service == null) {
            log.debug("EmailService not available, skipping password reset email to {}", event.email)
            return
        }

        val resetUrl = "$passwordResetBaseUrl?token=${event.resetToken}"
        val (textBody, htmlBody) = AuthEmailTemplates.passwordResetEmail(event.username, resetUrl)
        service.send(
            EmailRequest(
                to = event.email,
                subject = "Reset your password — jorisjonkers.dev",
                textBody = textBody,
                htmlBody = htmlBody,
            ),
        )
    }
}
