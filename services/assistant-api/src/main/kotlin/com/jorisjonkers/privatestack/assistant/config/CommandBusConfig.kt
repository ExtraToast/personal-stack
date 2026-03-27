package com.jorisjonkers.privatestack.assistant.config

import com.jorisjonkers.privatestack.common.command.Command
import com.jorisjonkers.privatestack.common.command.CommandBus
import com.jorisjonkers.privatestack.common.command.CommandHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.reflect.KClass

@Configuration
class CommandBusConfig {
    @Bean
    fun commandBus(handlers: List<CommandHandler<*>>): CommandBus = SpringCommandBus(handlers)
}

class SpringCommandBus(
    handlers: List<CommandHandler<*>>,
) : CommandBus {
    private val handlerMap: Map<KClass<*>, CommandHandler<*>> =
        buildMap {
            for (handler in handlers) {
                val commandType =
                    resolveCommandType(handler)
                        ?: error("Cannot determine command type for ${handler::class.simpleName}")
                put(commandType, handler)
            }
        }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Command> dispatch(command: T) {
        val handler =
            handlerMap[command::class] as? CommandHandler<T>
                ?: error("No handler registered for ${command::class.simpleName}")
        handler.handle(command)
    }

    private fun resolveCommandType(handler: CommandHandler<*>): KClass<*>? =
        handler::class
            .supertypes
            .firstOrNull { it.classifier == CommandHandler::class }
            ?.arguments
            ?.firstOrNull()
            ?.type
            ?.classifier as? KClass<*>
}
