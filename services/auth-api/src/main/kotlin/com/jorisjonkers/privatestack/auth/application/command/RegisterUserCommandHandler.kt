package com.jorisjonkers.privatestack.auth.application.command

import com.jorisjonkers.privatestack.auth.domain.event.UserRegisteredEvent
import com.jorisjonkers.privatestack.auth.domain.exception.DuplicateEmailException
import com.jorisjonkers.privatestack.auth.domain.exception.DuplicateUsernameException
import com.jorisjonkers.privatestack.auth.domain.model.Role
import com.jorisjonkers.privatestack.auth.domain.model.User
import com.jorisjonkers.privatestack.auth.domain.model.UserId
import com.jorisjonkers.privatestack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.privatestack.auth.domain.port.UserRepository
import com.jorisjonkers.privatestack.common.command.CommandHandler
import com.jorisjonkers.privatestack.common.messaging.RabbitMqConfig
import com.jorisjonkers.privatestack.common.messaging.RabbitMqEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class RegisterUserCommandHandler(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val eventPublisher: ApplicationEventPublisher,
    private val rabbitMqEventPublisher: RabbitMqEventPublisher,
) : CommandHandler<RegisterUserCommand> {
    override fun handle(command: RegisterUserCommand) {
        if (userRepository.existsByUsername(command.username)) {
            throw DuplicateUsernameException(command.username)
        }
        if (userRepository.existsByEmail(command.email)) {
            throw DuplicateEmailException(command.email)
        }

        val now = Instant.now()
        val user =
            User(
                id = UserId(UUID.randomUUID()),
                username = command.username,
                email = command.email,
                role = Role.USER,
                totpEnabled = false,
                createdAt = now,
                updatedAt = now,
            )
        val passwordHash = passwordEncoder.encode(command.password)
        val savedUser = userRepository.create(user, passwordHash)

        val event =
            UserRegisteredEvent(
                userId = savedUser.id,
                username = savedUser.username,
            )
        // Intra-service event (Spring Modulith)
        eventPublisher.publishEvent(event)
        // Inter-service event (RabbitMQ for other services to consume)
        rabbitMqEventPublisher.publish(RabbitMqConfig.USER_REGISTERED_ROUTING_KEY, event)
    }
}
