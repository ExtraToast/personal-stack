package com.jorisjonkers.personalstack.auth.domain.port

import com.jorisjonkers.personalstack.auth.domain.model.PasswordResetToken
import com.jorisjonkers.personalstack.auth.domain.model.UserId

interface PasswordResetTokenRepository {
    fun save(token: PasswordResetToken): PasswordResetToken

    fun findByToken(token: String): PasswordResetToken?

    fun deleteByUserId(userId: UserId)
}
