package com.jorisjonkers.personalstack.auth.domain.port

import com.jorisjonkers.personalstack.auth.domain.model.EmailConfirmationToken
import com.jorisjonkers.personalstack.auth.domain.model.UserId

interface EmailConfirmationTokenRepository {
    fun save(token: EmailConfirmationToken): EmailConfirmationToken
    fun findByToken(token: String): EmailConfirmationToken?
    fun deleteByUserId(userId: UserId)
}
