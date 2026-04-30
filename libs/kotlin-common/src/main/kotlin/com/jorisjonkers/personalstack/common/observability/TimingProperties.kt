package com.jorisjonkers.personalstack.common.observability

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Thresholds for cross-cutting timing logs in libs/kotlin-common.
 *
 * Anything slower than the relevant threshold is logged at INFO; faster
 * calls are logged at DEBUG. Tweak via application.yml without touching
 * commons.
 */
@ConfigurationProperties(prefix = "personalstack.timing")
data class TimingProperties(
    val enabled: Boolean = true,
    val slowRequestMs: Long = 500,
    val slowQueryMs: Long = 50,
    val slowMethodMs: Long = 25,
)
