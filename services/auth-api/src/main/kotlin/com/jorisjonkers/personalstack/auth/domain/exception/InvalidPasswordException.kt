package com.jorisjonkers.personalstack.auth.domain.exception

import com.jorisjonkers.personalstack.common.exception.DomainException

class InvalidPasswordException(
    detail: String = "Current password is incorrect",
) : DomainException(detail, "INVALID_PASSWORD")
