package com.jorisjonkers.personalstack.common.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps every HTTP request with wall-clock timing. Ordered as high as
 * possible so the measured window includes Spring Security, CSRF, and
 * any other downstream filters — that way a 13s cold call shows up here
 * regardless of which inner layer is responsible.
 *
 * On the very first request after boot it stamps `cold=true` so a
 * one-shot warm-up cost is unambiguous in the logs.
 */
class RequestTimingFilter(
    private val properties: TimingProperties,
) : OncePerRequestFilter(),
    Ordered {
    private val coldFlag = AtomicBoolean(true)

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + FILTER_ORDER_OFFSET

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val ownsRequestId = MDC.get(MDC_REQUEST_ID).isNullOrBlank()
        if (ownsRequestId) {
            MDC.put(MDC_REQUEST_ID, UUID.randomUUID().toString())
        }
        val cold = coldFlag.compareAndSet(true, false)
        val startNs = System.nanoTime()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs = (System.nanoTime() - startNs) / NS_PER_MS
            val slow = durationMs >= properties.slowRequestMs || cold
            TimingLog.emit(
                slow = slow,
                kind = "http_request",
                durationMs = durationMs,
                kvs =
                    mapOf(
                        "method" to request.method,
                        "path" to request.requestURI,
                        "query" to request.queryString,
                        "status" to response.status,
                        "cold" to if (cold) true else null,
                    ),
            )
            if (ownsRequestId) MDC.remove(MDC_REQUEST_ID)
        }
    }

    companion object {
        const val MDC_REQUEST_ID = "requestId"
        private const val FILTER_ORDER_OFFSET = 10
    }
}
