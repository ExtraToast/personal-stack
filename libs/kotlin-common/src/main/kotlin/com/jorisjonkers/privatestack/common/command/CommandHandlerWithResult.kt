package com.jorisjonkers.privatestack.common.command

interface CommandHandlerWithResult<T : Command, R> {
    fun handle(command: T): R
}
