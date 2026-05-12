package com.jorisjonkers.personalstack.auth.config

import com.jorisjonkers.personalstack.auth.domain.model.User
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Duration

/**
 * Valkey-backed Spring Cache wiring. Caches User-profile reads only.
 * Credentials (password hash, TOTP secret) are intentionally read
 * straight from Postgres on every login — see the comment on the
 * cache name constants below.
 */
@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
        val mapper = buildObjectMapper()
        val perCache =
            mapOf(
                CACHE_USERS_BY_ID to configFor(mapper, User::class.java, USER_CACHE_TTL),
                CACHE_USERS_BY_USERNAME to configFor(mapper, User::class.java, USER_CACHE_TTL),
                CACHE_USERS_BY_EMAIL to configFor(mapper, User::class.java, USER_CACHE_TTL),
            )
        return RedisCacheManager
            .builder(connectionFactory)
            .cacheDefaults(configFor(mapper, User::class.java, USER_CACHE_TTL))
            .withInitialCacheConfigurations(perCache)
            .build()
    }

    /**
     * Typed per-cache config. Each cache carries its own
     * [JacksonJsonRedisSerializer]<T> so deserialisation knows the
     * concrete class without needing default-typing metadata in the
     * payload. Avoids the polymorphic-typing rabbit hole that Kotlin
     * value classes (e.g. [com.jorisjonkers.personalstack.auth.domain.model.UserId])
     * drag you into.
     */
    private fun <T : Any> configFor(
        mapper: ObjectMapper,
        type: Class<T>,
        ttl: Duration,
    ): RedisCacheConfiguration =
        RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(ttl)
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()),
            ).serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer<T>(JacksonJsonRedisSerializer(mapper, type)),
            ).disableCachingNullValues()

    private fun buildObjectMapper(): ObjectMapper =
        JsonMapper
            .builder()
            // Kotlin module: construct data / value classes via their
            // primary constructors. Jackson 3 has built-in java.time
            // support, so no JavaTimeModule needed.
            .addModule(KotlinModule.Builder().build())
            .build()

    companion object {
        const val CACHE_USERS_BY_ID = "users.byId"
        const val CACHE_USERS_BY_USERNAME = "users.byUsername"
        const val CACHE_USERS_BY_EMAIL = "users.byEmail"

        // Profile caches: 5-minute TTL as a safety net for mutations
        // that bypass the repository (e.g. operator patches a row via
        // psql); every in-app mutator evicts precisely.
        private val USER_CACHE_TTL: Duration = Duration.ofMinutes(5)

        // findCredentialsByUsername (login hot path) is deliberately
        // not cached. A previous credentialsByUsername cache caused a
        // cohort of integration-test flakes — Spring's @Cacheable PUT
        // races a subsequent same-thread evict under Lettuce, so a
        // password rotation + immediate re-login could read stale
        // hash. Login is ~1 query per session, which Postgres handles
        // trivially on this stack.
    }
}
