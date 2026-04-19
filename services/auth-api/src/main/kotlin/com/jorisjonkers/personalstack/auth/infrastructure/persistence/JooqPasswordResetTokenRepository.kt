package com.jorisjonkers.personalstack.auth.infrastructure.persistence

import com.jorisjonkers.personalstack.auth.domain.model.PasswordResetToken
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.PasswordResetTokenRepository
import com.jorisjonkers.personalstack.auth.jooq.tables.PasswordResetToken.PASSWORD_RESET_TOKEN
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqPasswordResetTokenRepository(
    private val dsl: DSLContext,
) : PasswordResetTokenRepository {
    override fun save(token: PasswordResetToken): PasswordResetToken {
        val expiresAt = token.expiresAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
        val createdAt = token.createdAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
        val usedAt = token.usedAt?.atOffset(ZoneOffset.UTC)?.toLocalDateTime()

        dsl
            .insertInto(PASSWORD_RESET_TOKEN)
            .set(PASSWORD_RESET_TOKEN.ID, token.id)
            .set(PASSWORD_RESET_TOKEN.USER_ID, token.userId.value)
            .set(PASSWORD_RESET_TOKEN.TOKEN, token.token)
            .set(PASSWORD_RESET_TOKEN.EXPIRES_AT, expiresAt)
            .set(PASSWORD_RESET_TOKEN.USED_AT, usedAt)
            .set(PASSWORD_RESET_TOKEN.CREATED_AT, createdAt)
            .onConflict(PASSWORD_RESET_TOKEN.ID)
            .doUpdate()
            .set(PASSWORD_RESET_TOKEN.USED_AT, usedAt)
            .execute()

        return token
    }

    override fun findByToken(token: String): PasswordResetToken? =
        dsl
            .selectFrom(PASSWORD_RESET_TOKEN)
            .where(PASSWORD_RESET_TOKEN.TOKEN.eq(token))
            .fetchOne()
            ?.toDomain()

    override fun deleteByUserId(userId: UserId) {
        dsl
            .deleteFrom(PASSWORD_RESET_TOKEN)
            .where(PASSWORD_RESET_TOKEN.USER_ID.eq(userId.value))
            .execute()
    }

    private fun Record.toDomain(): PasswordResetToken =
        PasswordResetToken(
            id = this[PASSWORD_RESET_TOKEN.ID] as UUID,
            userId = UserId(this[PASSWORD_RESET_TOKEN.USER_ID] as UUID),
            token = this[PASSWORD_RESET_TOKEN.TOKEN] as String,
            expiresAt =
                (this[PASSWORD_RESET_TOKEN.EXPIRES_AT] as java.time.LocalDateTime)
                    .toInstant(ZoneOffset.UTC),
            usedAt =
                this[PASSWORD_RESET_TOKEN.USED_AT]
                    ?.toInstant(ZoneOffset.UTC),
            createdAt =
                (this[PASSWORD_RESET_TOKEN.CREATED_AT] as java.time.LocalDateTime)
                    .toInstant(ZoneOffset.UTC),
        )
}
