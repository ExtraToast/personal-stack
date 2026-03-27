package com.jorisjonkers.personalstack.auth.infrastructure.email

import com.jorisjonkers.personalstack.auth.domain.event.EmailConfirmationRequestedEvent
import com.jorisjonkers.personalstack.common.email.EmailRequest
import com.jorisjonkers.personalstack.common.email.EmailService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(EmailService::class)
class EmailConfirmationEmailListener(
    private val emailService: EmailService,
    @Value("\${app.confirmation-url:http://localhost:5174/confirm-email}")
    private val confirmationBaseUrl: String,
) {
    @EventListener
    fun onEmailConfirmationRequested(event: EmailConfirmationRequestedEvent) {
        val confirmUrl = "$confirmationBaseUrl?token=${event.confirmationToken}"
        val (textBody, htmlBody) = AuthEmailTemplates.confirmationEmail(event.username, confirmUrl)
        emailService.send(
            EmailRequest(
                to = event.email,
                subject = "Confirm your email — jorisjonkers.dev",
                textBody = textBody,
                htmlBody = htmlBody,
            ),
        )
    }
}
