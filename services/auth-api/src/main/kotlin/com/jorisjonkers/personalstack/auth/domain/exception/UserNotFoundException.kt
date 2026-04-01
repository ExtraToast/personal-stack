package com.jorisjonkers.personalstack.auth.domain.exception

import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.common.exception.DomainException

class UserNotFoundException(
    userId: UserId,
) : DomainException("User '${userId.value}' not found", "USER_NOT_FOUND")
