package com.jorisjonkers.personalstack.common.timing

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.jooq.ExecuteContext
import org.jooq.ExecuteListener
import org.slf4j.LoggerFactory
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

/**
 * Records one `personal_stack.db.query` Observation per jOOQ statement.
 * The same call site emits a Micrometer Timer (broken down per
 * `type=SELECT|INSERT|…`) AND a child OTel span that nests under the
 * request span — so a Tempo waterfall shows every DB hop the request
 * made and how long each took.
 *
 * Still accumulates per-request `query_count` + total query nanos onto
 * the current `HttpServletRequest`'s attributes (jOOQ runs on whatever
 * thread Spring picks; we go via `RequestContextHolder` to find the
 * active servlet request), so `RequestTimingFilter` can attach those
 * counters as high-cardinality tags on the request observation and the
 * legacy log line continues to summarise N+1 patterns like
 * `queries=42 query_ms=950`.
 *
 * Wired into Spring Boot's auto-configured `DSLContext` via a
 * `@Bean DefaultExecuteListenerProvider` from `TimingAutoConfiguration`.
 */
class JooqQueryTimingListener(
    private val observationRegistry: ObservationRegistry = ObservationRegistry.NOOP,
) : ExecuteListener {
    private val logger = LoggerFactory.getLogger("com.jorisjonkers.personalstack.common.timing.jooq")

    override fun executeStart(ctx: ExecuteContext) {
        val observation =
            Observation
                .createNotStarted(OBSERVATION_NAME, observationRegistry)
                .lowCardinalityKeyValue("type", ctx.type().name)
                .start()
        val scope = observation.openScope()
        ctx.data(START_NANOS_KEY, System.nanoTime())
        ctx.data(OBSERVATION_KEY, observation)
        ctx.data(SCOPE_KEY, scope)
    }

    override fun executeEnd(ctx: ExecuteContext) {
        val observation = ctx.data(OBSERVATION_KEY) as? Observation
        val scope = ctx.data(SCOPE_KEY) as? Observation.Scope
        val start = ctx.data(START_NANOS_KEY) as? Long ?: return
        val durationNanos = System.nanoTime() - start
        val durationMs = durationNanos / RequestTimingAttributes.NANOS_PER_MILLI
        val sql = ctx.sql()?.take(SQL_LOG_LIMIT) ?: "<no sql>"
        try {
            // SQL is high-cardinality (every parameter combination is a
            // distinct string) so it never becomes a Micrometer tag, but it
            // is useful on the trace span where Tempo doesn't index by it.
            observation?.highCardinalityKeyValue("db.statement", sql)
        } finally {
            scope?.close()
            observation?.stop()
        }
        logger.info(
            "[jooq] type={} duration_ms={} sql={}",
            ctx.type(),
            durationMs,
            sql,
        )
        accumulateOnRequest(durationNanos)
    }

    private fun accumulateOnRequest(durationNanos: Long) {
        // No-op when the listener fires outside an HTTP request scope
        // (Flyway migrations, scheduled jobs, startup wiring, tests).
        val attrs = RequestContextHolder.getRequestAttributes() ?: return
        val scope = RequestAttributes.SCOPE_REQUEST
        val count = (attrs.getAttribute(RequestTimingAttributes.QUERY_COUNT, scope) as? Int ?: 0) + 1
        val total =
            (attrs.getAttribute(RequestTimingAttributes.TOTAL_QUERY_NANOS, scope) as? Long ?: 0L) + durationNanos
        attrs.setAttribute(RequestTimingAttributes.QUERY_COUNT, count, scope)
        attrs.setAttribute(RequestTimingAttributes.TOTAL_QUERY_NANOS, total, scope)
    }

    companion object {
        const val OBSERVATION_NAME: String = "personal_stack.db.query"
        private const val START_NANOS_KEY = "personal-stack.timing.start_nanos"
        private const val OBSERVATION_KEY = "personal-stack.timing.jooq_observation"
        private const val SCOPE_KEY = "personal-stack.timing.jooq_scope"
        private const val SQL_LOG_LIMIT = 200
    }
}
