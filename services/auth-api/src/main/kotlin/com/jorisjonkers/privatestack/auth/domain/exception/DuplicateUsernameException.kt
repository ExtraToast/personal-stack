package com.jorisjonkers.privatestack.auth.domain.exception

import com.jorisjonkers.privatestack.common.exception.DomainException

class DuplicateUsernameException(
    username: String,
) : DomainException("Username '$username' is already taken", "DUPLICATE_USERNAME")
