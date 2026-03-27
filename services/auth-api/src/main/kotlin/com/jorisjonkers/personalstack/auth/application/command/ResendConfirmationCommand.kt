package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.common.command.Command

data class ResendConfirmationCommand(val email: String) : Command
