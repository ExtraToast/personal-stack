package com.jorisjonkers.personalstack.auth.infrastructure.persistence

import com.jorisjonkers.personalstack.auth.domain.model.EmailConfirmationToken
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.EmailConfirmationTokenRepository
import com.jorisjonkers.personalstack.auth.jooq.tables.EmailConfirmationToken.EMAIL_CONFIRMATION_TOKEN
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqEmailConfirmationTokenRepository(
    private val dsl: DSLContext,
) : EmailConfirmationTokenRepository {
    override fun save(token: EmailConfirmationToken): EmailConfirmationToken {
        val expiresAt = token.expiresAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
        val createdAt = token.createdAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
        val usedAt = token.usedAt?.atOffset(ZoneOffset.UTC)?.toLocalDateTime()

        dsl
            .insertInto(EMAIL_CONFIRMATION_TOKEN)
            .set(EMAIL_CONFIRMATION_TOKEN.ID, token.id)
            .set(EMAIL_CONFIRMATION_TOKEN.USER_ID, token.userId.value)
            .set(EMAIL_CONFIRMATION_TOKEN.TOKEN, token.token)
            .set(EMAIL_CONFIRMATION_TOKEN.EXPIRES_AT, expiresAt)
            .set(EMAIL_CONFIRMATION_TOKEN.USED_AT, usedAt)
            .set(EMAIL_CONFIRMATION_TOKEN.CREATED_AT, createdAt)
            .onConflict(EMAIL_CONFIRMATION_TOKEN.ID)
            .doUpdate()
            .set(EMAIL_CONFIRMATION_TOKEN.USED_AT, usedAt)
            .execute()

        return token
    }

    override fun findByToken(token: String): EmailConfirmationToken? =
        dsl
            .selectFrom(EMAIL_CONFIRMATION_TOKEN)
            .where(EMAIL_CONFIRMATION_TOKEN.TOKEN.eq(token))
            .fetchOne()
            ?.toDomain()

    override fun deleteByUserId(userId: UserId) {
        dsl
            .deleteFrom(EMAIL_CONFIRMATION_TOKEN)
            .where(EMAIL_CONFIRMATION_TOKEN.USER_ID.eq(userId.value))
            .execute()
    }

    private fun Record.toDomain(): EmailConfirmationToken =
        EmailConfirmationToken(
            id = this[EMAIL_CONFIRMATION_TOKEN.ID] as UUID,
            userId = UserId(this[EMAIL_CONFIRMATION_TOKEN.USER_ID] as UUID),
            token = this[EMAIL_CONFIRMATION_TOKEN.TOKEN] as String,
            expiresAt =
                (this[EMAIL_CONFIRMATION_TOKEN.EXPIRES_AT] as java.time.LocalDateTime)
                    .toInstant(ZoneOffset.UTC),
            usedAt =
                this[EMAIL_CONFIRMATION_TOKEN.USED_AT]
                    ?.toInstant(ZoneOffset.UTC),
            createdAt =
                (this[EMAIL_CONFIRMATION_TOKEN.CREATED_AT] as java.time.LocalDateTime)
                    .toInstant(ZoneOffset.UTC),
        )
}
