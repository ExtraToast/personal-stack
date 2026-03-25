package com.jorisjonkers.privatestack.auth.domain.exception

import com.jorisjonkers.privatestack.common.exception.DomainException

class DuplicateEmailException(email: String) :
    DomainException("Email '$email' is already registered", "DUPLICATE_EMAIL")
