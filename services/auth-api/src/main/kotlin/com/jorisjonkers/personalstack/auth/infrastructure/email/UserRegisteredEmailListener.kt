package com.jorisjonkers.personalstack.auth.infrastructure.email

import com.jorisjonkers.personalstack.auth.domain.event.UserRegisteredEvent
import com.jorisjonkers.personalstack.common.email.EmailRequest
import com.jorisjonkers.personalstack.common.email.EmailService
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(EmailService::class)
class UserRegisteredEmailListener(
    private val emailService: EmailService,
) {
    @EventListener
    fun onUserRegistered(event: UserRegisteredEvent) {
        val (textBody, htmlBody) = AuthEmailTemplates.welcomeEmail(event.username)
        emailService.send(
            EmailRequest(
                to = event.email,
                subject = "Welcome to jorisjonkers.dev",
                textBody = textBody,
                htmlBody = htmlBody,
            ),
        )
    }
}
