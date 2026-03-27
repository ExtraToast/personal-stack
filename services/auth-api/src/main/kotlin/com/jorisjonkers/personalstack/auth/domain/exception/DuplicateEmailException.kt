package com.jorisjonkers.personalstack.auth.domain.exception

import com.jorisjonkers.personalstack.common.exception.DomainException

class DuplicateEmailException(
    email: String,
) : DomainException("Email '$email' is already registered", "DUPLICATE_EMAIL")
