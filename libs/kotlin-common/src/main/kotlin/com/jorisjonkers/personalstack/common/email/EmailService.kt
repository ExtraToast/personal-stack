package com.jorisjonkers.personalstack.common.email

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnClass(JavaMailSender::class)
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${app.mail.from:auth@jorisjonkers.dev}")
    private val fromAddress: String,
    @Value("\${app.mail.from-name:jorisjonkers.dev}")
    private val fromName: String,
    @Value("\${app.mail.max-retries:3}")
    private val maxRetries: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun send(request: EmailRequest) {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                doSend(request)
                log.info(
                    "Email sent to={} subject=\"{}\" attempt={}/{}",
                    request.to,
                    request.subject,
                    attempt,
                    maxRetries,
                )
                return
            } catch (e: MailException) {
                lastException = e
                log.warn(
                    "Email send failed to={} subject=\"{}\" attempt={}/{}: {}",
                    request.to,
                    request.subject,
                    attempt,
                    maxRetries,
                    e.message,
                )
                if (attempt < maxRetries) {
                    Thread.sleep(attempt * 1000L)
                }
            }
        }
        log.error(
            "Email delivery failed after {} attempts to={} subject=\"{}\"",
            maxRetries,
            request.to,
            request.subject,
            lastException,
        )
    }

    private fun doSend(request: EmailRequest) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setFrom(request.from ?: fromAddress, request.fromName ?: fromName)
        helper.setTo(request.to)
        helper.setSubject(request.subject)

        if (request.htmlBody != null) {
            helper.setText(request.textBody ?: stripHtml(request.htmlBody), request.htmlBody)
        } else {
            helper.setText(request.textBody ?: "")
        }

        request.replyTo?.let { helper.setReplyTo(it) }

        mailSender.send(message)
    }

    private fun stripHtml(html: String): String =
        html
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
}
