package com.jorisjonkers.personalstack.common.timing

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping

/**
 * Brackets every HTTP request with a `personal_stack.request` Observation
 * — one call site fans out into a per-URI Micrometer Timer (Prometheus
 * histogram for the burndown dashboards) AND an OTel span (Tempo
 * waterfall). The legacy log line is kept for parity with phase 1 of
 * the timing work, but its logger defaults to WARN in production via
 * logback so it falls silent unless explicitly bumped.
 *
 * Reads request attributes set by the jOOQ listener — `null` accumulators
 * mean the request issued no DB calls, which is itself useful diagnostic
 * info ("`queries=0 query_ms=0`" with a 12 s total → time is in
 * filters / Redis / serialization, not the database).
 */
class RequestTimingFilter(
    private val observationRegistry: ObservationRegistry = ObservationRegistry.NOOP,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val observation =
            Observation
                .createNotStarted(OBSERVATION_NAME, observationRegistry)
                .lowCardinalityKeyValue("method", request.method)
                .start()
        val scope = observation.openScope()
        val startNanos = System.nanoTime()
        try {
            filterChain.doFilter(request, response)
        } catch (t: Throwable) {
            observation.error(t)
            throw t
        } finally {
            val durationMs = (System.nanoTime() - startNanos) / RequestTimingAttributes.NANOS_PER_MILLI
            val query = request.queryString
            val path = if (query.isNullOrEmpty()) request.requestURI else "${request.requestURI}?$query"
            val queryCount = request.getAttribute(RequestTimingAttributes.QUERY_COUNT) as? Int ?: 0
            val queryNanos = request.getAttribute(RequestTimingAttributes.TOTAL_QUERY_NANOS) as? Long ?: 0L
            val queryMs = queryNanos / RequestTimingAttributes.NANOS_PER_MILLI
            // Late-bind the high-signal tags now that the chain has run and
            // Spring has populated the matched URI template attribute. The
            // template (`/api/users/{id}`) is what dashboards filter on —
            // not the concrete URL, which would explode metric cardinality.
            observation
                .lowCardinalityKeyValue("uri", uriTemplate(request))
                .lowCardinalityKeyValue("status", response.status.toString())
                .lowCardinalityKeyValue("outcome", outcome(response.status))
                .highCardinalityKeyValue("http.target", path)
                .highCardinalityKeyValue("db.query_count", queryCount.toString())
                .highCardinalityKeyValue("db.query_ms", queryMs.toString())
            scope.close()
            observation.stop()
            timingLogger.info(
                "[request] {} {} status={} duration_ms={} queries={} query_ms={}",
                request.method,
                path,
                response.status,
                durationMs,
                queryCount,
                queryMs,
            )
        }
    }

    private fun uriTemplate(request: HttpServletRequest): String {
        val matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
        return matched ?: "UNKNOWN"
    }

    private fun outcome(status: Int): String =
        when (status / 100) {
            1 -> "INFORMATIONAL"
            2 -> "SUCCESS"
            3 -> "REDIRECTION"
            4 -> "CLIENT_ERROR"
            5 -> "SERVER_ERROR"
            else -> "UNKNOWN"
        }

    companion object {
        const val OBSERVATION_NAME: String = "personal_stack.request"
        private val timingLogger = LoggerFactory.getLogger("com.jorisjonkers.personalstack.common.timing.request")
    }
}
