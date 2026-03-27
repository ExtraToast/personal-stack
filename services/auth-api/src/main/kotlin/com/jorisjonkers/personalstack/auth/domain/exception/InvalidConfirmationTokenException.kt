package com.jorisjonkers.personalstack.auth.domain.exception

import com.jorisjonkers.personalstack.common.exception.DomainException

class InvalidConfirmationTokenException(
    detail: String,
) : DomainException(detail, "INVALID_CONFIRMATION_TOKEN")
