package com.jorisjonkers.personalstack.auth.domain.exception

import com.jorisjonkers.personalstack.common.exception.DomainException

class EmailNotConfirmedException :
    DomainException("Email address has not been confirmed", "EMAIL_NOT_CONFIRMED")
