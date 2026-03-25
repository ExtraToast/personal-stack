package com.jorisjonkers.privatestack.common.command

interface CommandHandler<T : Command> {
    fun handle(command: T)
}
