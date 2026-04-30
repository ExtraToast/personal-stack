package com.jorisjonkers.personalstack.common.observability

import org.jooq.ExecuteContext
import org.jooq.ExecuteListener

/**
 * jOOQ ExecuteListener that times every executed statement and logs
 * total wall-clock plus a redacted SQL preview. Wired in via a
 * DefaultConfigurationCustomizer so Spring Boot's auto-configured
 * DSLContext picks it up.
 */
class JooqTimingListener(
    private val properties: TimingProperties,
) : ExecuteListener {
    override fun executeStart(ctx: ExecuteContext) {
        ctx.data(TIMER_KEY, System.nanoTime())
    }

    override fun executeEnd(ctx: ExecuteContext) {
        val started = ctx.data(TIMER_KEY) as? Long ?: return
        val durationMs = (System.nanoTime() - started) / NS_PER_MS
        val slow = durationMs >= properties.slowQueryMs
        TimingLog.emit(
            slow = slow,
            kind = "jooq_query",
            durationMs = durationMs,
            kvs =
                mapOf(
                    "type" to ctx.type().name,
                    "rows" to ctx.rows().takeIf { it >= 0 },
                    "sql" to ctx.sql()?.let { sqlPreview(it) },
                ),
        )
    }

    private fun sqlPreview(sql: String): String {
        val collapsed = sql.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        return if (collapsed.length <= MAX_SQL_LENGTH) collapsed else collapsed.take(MAX_SQL_LENGTH) + "…"
    }

    companion object {
        private const val TIMER_KEY = "personalstack.timing.start_ns"
        private const val MAX_SQL_LENGTH = 500
    }
}
