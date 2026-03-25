package com.jorisjonkers.privatestack.common.command

interface CommandBus {
    fun <T : Command> dispatch(command: T)
}
