package com.jorisjonkers.personalstack.common.observability

import org.slf4j.LoggerFactory

internal const val NS_PER_MS = 1_000_000L

/**
 * Single shared logger for cross-cutting timing emissions so consumers
 * (Loki / Grafana) can filter on a stable logger name.
 */
internal object TimingLog {
    val log = LoggerFactory.getLogger("com.jorisjonkers.personalstack.common.observability.Timing")

    fun emit(
        slow: Boolean,
        kind: String,
        durationMs: Long,
        kvs: Map<String, Any?>,
    ) {
        if (!slow && !log.isDebugEnabled) return
        val rendered =
            buildString {
                append(kind)
                append(" duration_ms=").append(durationMs)
                kvs.forEach { (k, v) ->
                    if (v == null) return@forEach
                    append(' ').append(k).append('=').append(format(v))
                }
            }
        if (slow) log.info(rendered) else log.debug(rendered)
    }

    private fun format(value: Any): String {
        val s = value.toString()
        return if (s.any { it == ' ' || it == '"' || it == '=' }) {
            "\"${s.replace("\"", "\\\"")}\""
        } else {
            s
        }
    }
}
