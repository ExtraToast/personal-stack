package com.jorisjonkers.privatestack.auth.config

import com.jorisjonkers.privatestack.common.command.Command
import com.jorisjonkers.privatestack.common.command.CommandBus
import com.jorisjonkers.privatestack.common.command.CommandHandler
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CommandBusConfig {

    @Bean
    fun commandBus(applicationContext: ApplicationContext): CommandBus {
        return SpringCommandBus(applicationContext)
    }
}

class SpringCommandBus(
    private val applicationContext: ApplicationContext,
) : CommandBus {

    @Suppress("UNCHECKED_CAST")
    override fun <T : Command> dispatch(command: T) {
        val handlers = applicationContext.getBeansOfType(CommandHandler::class.java)
        val handler = handlers.values
            .filterIsInstance<CommandHandler<T>>()
            .firstOrNull() ?: error("No handler found for ${command::class.simpleName}")
        handler.handle(command)
    }
}
