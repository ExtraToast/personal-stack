package com.jorisjonkers.personalstack.auth.domain.exception

import com.jorisjonkers.personalstack.common.exception.DomainException

class DuplicateUsernameException(
    username: String,
) : DomainException("Username '$username' is already taken", "DUPLICATE_USERNAME")
