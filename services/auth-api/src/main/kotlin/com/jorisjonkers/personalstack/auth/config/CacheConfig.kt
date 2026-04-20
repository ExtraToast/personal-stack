package com.jorisjonkers.personalstack.auth.config

import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
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
 * Valkey-backed Spring Cache wiring. Spring Session already uses the
 * Valkey pod in `data-system`; caching piggybacks on the same
 * connection factory via spring-boot-starter-data-redis.
 *
 * Namespaces (== cache names) live on top-level keys:
 *   users.byId                 — User by UserId
 *   users.byUsername           — User by username
 *   users.byEmail              — User by email
 *   users.credentialsByUsername — UserCredentials by username (used on the
 *                                hot /session-login path; shorter TTL because
 *                                it rides password/TOTP changes)
 *
 * Per-cache TTLs keep stale-read windows bounded even in the
 * pathological case where a mutator's @CacheEvict is skipped by
 * self-invocation (none today — all annotated methods are top-level
 * `override fun`s on the Spring-proxied bean).
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
                CACHE_USERS_CREDENTIALS_BY_USERNAME to
                    configFor(mapper, UserCredentials::class.java, CREDENTIALS_CACHE_TTL),
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
        const val CACHE_USERS_CREDENTIALS_BY_USERNAME = "users.credentialsByUsername"

        // User reads are mostly idempotent for the cache's lifetime:
        // profile data changes rarely (settings edits, role updates),
        // and every such mutation goes through an evicting repository
        // method. A 5-minute TTL is a safety net for mutations that
        // somehow skip the repository (e.g. an operator patches a row
        // via psql), not the primary consistency mechanism.
        private val USER_CACHE_TTL: Duration = Duration.ofMinutes(5)

        // Credentials (password hash, TOTP secret) change rarely in
        // absolute terms but the cost of a stale read is higher: a
        // rotated password that keeps authenticating for 5 minutes is
        // a security finding. 60 s keeps the cache useful for burst
        // traffic on the /session-login path without letting the
        // stale window linger.
        private val CREDENTIALS_CACHE_TTL: Duration = Duration.ofSeconds(60)
    }
}
