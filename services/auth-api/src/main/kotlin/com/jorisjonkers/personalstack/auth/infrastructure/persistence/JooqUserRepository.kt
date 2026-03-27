package com.jorisjonkers.personalstack.auth.infrastructure.persistence

import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.jooq.tables.AppUser.APP_USER
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqUserRepository(
    private val dsl: DSLContext,
) : UserRepository {
    override fun findById(id: UserId): User? =
        dsl
            .selectFrom(APP_USER)
            .where(APP_USER.ID.eq(id.value))
            .fetchOne()
            ?.toUser()

    override fun findByUsername(username: String): User? =
        dsl
            .selectFrom(APP_USER)
            .where(APP_USER.USERNAME.eq(username))
            .fetchOne()
            ?.toUser()

    override fun findByEmail(email: String): User? =
        dsl
            .selectFrom(APP_USER)
            .where(APP_USER.EMAIL.eq(email))
            .fetchOne()
            ?.toUser()

    override fun findCredentialsByUsername(username: String): UserCredentials? =
        dsl
            .selectFrom(APP_USER)
            .where(APP_USER.USERNAME.eq(username))
            .fetchOne()
            ?.toUserCredentials()

    override fun create(
        user: User,
        passwordHash: String,
    ): User {
        val now = user.createdAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
        dsl
            .insertInto(APP_USER)
            .set(APP_USER.ID, user.id.value)
            .set(APP_USER.USERNAME, user.username)
            .set(APP_USER.EMAIL, user.email)
            .set(APP_USER.PASSWORD_HASH, passwordHash)
            .set(APP_USER.TOTP_SECRET, null as String?)
            .set(APP_USER.EMAIL_CONFIRMED, user.emailConfirmed)
            .set(APP_USER.TOTP_ENABLED, false)
            .set(APP_USER.ROLE, user.role.name)
            .set(APP_USER.CREATED_AT, now)
            .set(APP_USER.UPDATED_AT, now)
            .execute()
        return user
    }

    override fun update(user: User): User {
        val now = user.updatedAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
        dsl
            .update(APP_USER)
            .set(APP_USER.EMAIL_CONFIRMED, user.emailConfirmed)
            .set(APP_USER.TOTP_ENABLED, user.totpEnabled)
            .set(APP_USER.ROLE, user.role.name)
            .set(APP_USER.UPDATED_AT, now)
            .where(APP_USER.ID.eq(user.id.value))
            .execute()
        return user
    }

    override fun saveTotpSecret(
        userId: UserId,
        secret: String,
    ) {
        dsl
            .update(APP_USER)
            .set(APP_USER.TOTP_SECRET, secret)
            .where(APP_USER.ID.eq(userId.value))
            .execute()
    }

    override fun existsByUsername(username: String): Boolean =
        dsl.fetchExists(dsl.selectFrom(APP_USER).where(APP_USER.USERNAME.eq(username)))

    override fun existsByEmail(email: String): Boolean =
        dsl.fetchExists(dsl.selectFrom(APP_USER).where(APP_USER.EMAIL.eq(email)))

    private fun Record.toUser(): User =
        User(
            id = UserId(this[APP_USER.ID] as UUID),
            username = this[APP_USER.USERNAME] as String,
            email = this[APP_USER.EMAIL] as String,
            role = Role.valueOf(this[APP_USER.ROLE] as String),
            emailConfirmed = this[APP_USER.EMAIL_CONFIRMED] as Boolean,
            totpEnabled = this[APP_USER.TOTP_ENABLED] as Boolean,
            createdAt =
                (this[APP_USER.CREATED_AT] as java.time.LocalDateTime)
                    .toInstant(ZoneOffset.UTC),
            updatedAt =
                (this[APP_USER.UPDATED_AT] as java.time.LocalDateTime)
                    .toInstant(ZoneOffset.UTC),
        )

    private fun Record.toUserCredentials(): UserCredentials =
        UserCredentials(
            userId = UserId(this[APP_USER.ID] as UUID),
            username = this[APP_USER.USERNAME] as String,
            passwordHash = this[APP_USER.PASSWORD_HASH] as String,
            totpSecret = this[APP_USER.TOTP_SECRET] as String?,
            totpEnabled = this[APP_USER.TOTP_ENABLED] as Boolean,
            emailConfirmed = this[APP_USER.EMAIL_CONFIRMED] as Boolean,
            role = Role.valueOf(this[APP_USER.ROLE] as String),
        )
}
