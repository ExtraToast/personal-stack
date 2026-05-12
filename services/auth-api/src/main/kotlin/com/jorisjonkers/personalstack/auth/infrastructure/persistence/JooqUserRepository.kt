package com.jorisjonkers.personalstack.auth.infrastructure.persistence

import com.jorisjonkers.personalstack.auth.config.CacheConfig.Companion.CACHE_USERS_BY_EMAIL
import com.jorisjonkers.personalstack.auth.config.CacheConfig.Companion.CACHE_USERS_BY_ID
import com.jorisjonkers.personalstack.auth.config.CacheConfig.Companion.CACHE_USERS_BY_USERNAME
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.jooq.tables.AppUser.APP_USER
import com.jorisjonkers.personalstack.auth.jooq.tables.UserServicePermissions.USER_SERVICE_PERMISSIONS
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Repository
import java.time.ZoneOffset
import java.util.UUID

@Repository
@Suppress("TooManyFunctions")
class JooqUserRepository(
    private val dsl: DSLContext,
    private val cacheManager: CacheManager,
) : UserRepository {
    private val logger = LoggerFactory.getLogger(JooqUserRepository::class.java)

    @Cacheable(cacheNames = [CACHE_USERS_BY_ID], key = "#id", unless = "#result == null")
    override fun findById(id: UserId): User? =
        dsl
            .select(*APP_USER.fields(), permissionsField)
            .from(APP_USER)
            .where(APP_USER.ID.eq(id.value))
            .fetchOne()
            ?.let { it.toUser(it.extractPermissions()) }

    @Cacheable(cacheNames = [CACHE_USERS_BY_USERNAME], key = "#username", unless = "#result == null")
    override fun findByUsername(username: String): User? =
        dsl
            .select(*APP_USER.fields(), permissionsField)
            .from(APP_USER)
            .where(APP_USER.USERNAME.eq(username))
            .fetchOne()
            ?.let { it.toUser(it.extractPermissions()) }

    @Cacheable(cacheNames = [CACHE_USERS_BY_EMAIL], key = "#email", unless = "#result == null")
    override fun findByEmail(email: String): User? =
        dsl
            .select(*APP_USER.fields(), permissionsField)
            .from(APP_USER)
            .where(APP_USER.EMAIL.eq(email))
            .fetchOne()
            ?.let { it.toUser(it.extractPermissions()) }

    override fun findCredentialsByUsername(username: String): UserCredentials? =
        dsl
            .select(*APP_USER.fields(), permissionsField)
            .from(APP_USER)
            .where(APP_USER.USERNAME.eq(username))
            .fetchOne()
            ?.let { it.toUserCredentials(it.extractPermissions()) }

    override fun findAll(): List<User> =
        dsl
            .select(*APP_USER.fields(), permissionsField)
            .from(APP_USER)
            .fetch()
            .map { it.toUser(it.extractPermissions()) }

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
            .set(APP_USER.FIRST_NAME, user.firstName)
            .set(APP_USER.LAST_NAME, user.lastName)
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

    // All mutators below evict every user cache by precise key. We used to
    // declare three of the four cache evictions as `allEntries = true` since
    // the mutators only know the UserId, but RedisCache.clear() is not
    // synchronous with respect to a subsequent get() on the same thread
    // under Lettuce — a user changing password and immediately re-logging
    // in would read stale credentials. Each mutator now resolves the user
    // first (a @Cacheable hit ~free) so we know username/email and can
    // issue per-key evict() calls, which are synchronous Redis DELs.
    override fun update(user: User): User {
        val now = user.updatedAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
        dsl
            .update(APP_USER)
            .set(APP_USER.FIRST_NAME, user.firstName)
            .set(APP_USER.LAST_NAME, user.lastName)
            .set(APP_USER.EMAIL_CONFIRMED, user.emailConfirmed)
            .set(APP_USER.TOTP_ENABLED, user.totpEnabled)
            .set(APP_USER.ROLE, user.role.name)
            .set(APP_USER.UPDATED_AT, now)
            .where(APP_USER.ID.eq(user.id.value))
            .execute()
        evictAllCachesFor(user.id, user.username, user.email)
        return user
    }

    override fun saveServicePermissions(
        userId: UserId,
        permissions: Set<ServicePermission>,
    ) {
        dsl
            .deleteFrom(USER_SERVICE_PERMISSIONS)
            .where(USER_SERVICE_PERMISSIONS.USER_ID.eq(userId.value))
            .execute()
        if (permissions.isNotEmpty()) {
            dsl
                .batch(
                    permissions.map { permission ->
                        dsl
                            .insertInto(USER_SERVICE_PERMISSIONS)
                            .set(USER_SERVICE_PERMISSIONS.USER_ID, userId.value)
                            .set(USER_SERVICE_PERMISSIONS.SERVICE, permission.name)
                    },
                ).execute()
        }
        evictAllCachesFor(userId)
    }

    override fun deleteById(id: UserId) {
        val keys = lookupNameKeys(id)
        dsl
            .deleteFrom(APP_USER)
            .where(APP_USER.ID.eq(id.value))
            .execute()
        evictAllCachesFor(id, keys?.first, keys?.second)
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
        evictAllCachesFor(userId)
    }

    override fun existsByUsername(username: String): Boolean =
        dsl.fetchExists(dsl.selectFrom(APP_USER).where(APP_USER.USERNAME.eq(username)))

    override fun existsByEmail(email: String): Boolean =
        dsl.fetchExists(dsl.selectFrom(APP_USER).where(APP_USER.EMAIL.eq(email)))

    override fun updatePassword(
        userId: UserId,
        passwordHash: String,
    ) {
        val now =
            java.time.Instant
                .now()
                .atOffset(ZoneOffset.UTC)
                .toLocalDateTime()
        dsl
            .update(APP_USER)
            .set(APP_USER.PASSWORD_HASH, passwordHash)
            .set(APP_USER.UPDATED_AT, now)
            .where(APP_USER.ID.eq(userId.value))
            .execute()
        evictAllCachesFor(userId)
    }

    private fun lookupNameKeys(userId: UserId): Pair<String, String>? =
        dsl
            .select(APP_USER.USERNAME, APP_USER.EMAIL)
            .from(APP_USER)
            .where(APP_USER.ID.eq(userId.value))
            .fetchOne()
            ?.let { it[APP_USER.USERNAME] to it[APP_USER.EMAIL] }

    private fun evictAllCachesFor(userId: UserId) {
        val keys = lookupNameKeys(userId) ?: return evictAllCachesFor(userId, null, null)
        evictAllCachesFor(userId, keys.first, keys.second)
    }

    private fun evictAllCachesFor(
        userId: UserId,
        username: String?,
        email: String?,
    ) {
        cacheManager.getCache(CACHE_USERS_BY_ID)?.evict(userId.value)
        username?.let { cacheManager.getCache(CACHE_USERS_BY_USERNAME)?.evict(it) }
        email?.let { cacheManager.getCache(CACHE_USERS_BY_EMAIL)?.evict(it) }
    }

    // Correlated jOOQ multiset subquery — emits a single SQL statement
    // per user fetch ("SELECT ..., ARRAY(SELECT service FROM
    // user_service_permissions WHERE user_id = app_user.id) ..."), so
    // findById / findByUsername / findCredentialsByUsername / findAll
    // each round-trip to Postgres exactly once. Previously the loader
    // issued an N+1 SELECT per user, which added ~1 query per login,
    // token mint, and /me call. Retains the runCatching tolerance from
    // PR #155 so rows for retired enum entries still degrade to a log
    // warn instead of a 500.
    private val permissionsField: Field<Set<ServicePermission>> =
        DSL
            .multiset(
                DSL
                    .select(USER_SERVICE_PERMISSIONS.SERVICE)
                    .from(USER_SERVICE_PERMISSIONS)
                    .where(USER_SERVICE_PERMISSIONS.USER_ID.eq(APP_USER.ID)),
            ).convertFrom { result ->
                result
                    .mapNotNull { row ->
                        val name = row[USER_SERVICE_PERMISSIONS.SERVICE] ?: return@mapNotNull null
                        runCatching { ServicePermission.valueOf(name) }
                            .onFailure {
                                logger.warn("Ignoring unknown service permission '{}'", name)
                            }.getOrNull()
                    }.toSet()
            }.`as`("service_permissions")

    private fun Record.extractPermissions(): Set<ServicePermission> {
        @Suppress("UNCHECKED_CAST")
        return (this[permissionsField.name] as? Set<ServicePermission>) ?: emptySet()
    }

    private fun Record.toUser(servicePermissions: Set<ServicePermission>): User {
        val userId = UserId(this[APP_USER.ID] as UUID)
        return User(
            id = userId,
            username = this[APP_USER.USERNAME] as String,
            email = this[APP_USER.EMAIL] as String,
            firstName = this[APP_USER.FIRST_NAME] as String,
            lastName = this[APP_USER.LAST_NAME] as String,
            role = Role.valueOf(this[APP_USER.ROLE] as String),
            emailConfirmed = this[APP_USER.EMAIL_CONFIRMED] as Boolean,
            totpEnabled = this[APP_USER.TOTP_ENABLED] as Boolean,
            createdAt = (this[APP_USER.CREATED_AT] as java.time.LocalDateTime).toInstant(ZoneOffset.UTC),
            updatedAt = (this[APP_USER.UPDATED_AT] as java.time.LocalDateTime).toInstant(ZoneOffset.UTC),
            servicePermissions = servicePermissions,
        )
    }

    private fun Record.toUserCredentials(servicePermissions: Set<ServicePermission>): UserCredentials =
        UserCredentials(
            userId = UserId(this[APP_USER.ID] as UUID),
            username = this[APP_USER.USERNAME] as String,
            email = this[APP_USER.EMAIL] as String,
            firstName = this[APP_USER.FIRST_NAME] as String,
            lastName = this[APP_USER.LAST_NAME] as String,
            passwordHash = this[APP_USER.PASSWORD_HASH] as String,
            totpSecret = this[APP_USER.TOTP_SECRET],
            totpEnabled = this[APP_USER.TOTP_ENABLED] as Boolean,
            emailConfirmed = this[APP_USER.EMAIL_CONFIRMED] as Boolean,
            role = Role.valueOf(this[APP_USER.ROLE] as String),
            servicePermissions = servicePermissions,
        )
}
