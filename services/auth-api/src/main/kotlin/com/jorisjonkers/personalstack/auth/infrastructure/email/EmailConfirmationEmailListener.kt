package com.jorisjonkers.personalstack.auth.infrastructure.email

import com.jorisjonkers.personalstack.auth.domain.event.EmailConfirmationRequestedEvent
import com.jorisjonkers.personalstack.common.email.EmailRequest
import com.jorisjonkers.personalstack.common.email.EmailService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.Optional

@Component
class EmailConfirmationEmailListener(
    private val emailService: Optional<EmailService>,
    @Value("\${app.confirmation-url:http://localhost:5174/confirm-email}")
    private val confirmationBaseUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onEmailConfirmationRequested(event: EmailConfirmationRequestedEvent) {
        val service = emailService.orElse(null)
        if (service == null) {
            log.debug("EmailService not available, skipping confirmation email to {}", event.email)
            return
        }

        val confirmUrl = "$confirmationBaseUrl?token=${event.confirmationToken}"
        val (textBody, htmlBody) = AuthEmailTemplates.confirmationEmail(event.username, confirmUrl)
        service.send(
            EmailRequest(
                to = event.email,
                subject = "Confirm your email — jorisjonkers.dev",
                textBody = textBody,
                htmlBody = htmlBody,
            ),
        )
    }
}
