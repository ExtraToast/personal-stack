package com.jorisjonkers.personalstack.auth.domain.exception

import com.jorisjonkers.personalstack.common.exception.DomainException

class InvalidResetTokenException(
    detail: String = "Password reset token is invalid",
) : DomainException(detail, "INVALID_RESET_TOKEN")
