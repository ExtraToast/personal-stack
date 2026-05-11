package com.jorisjonkers.personalstack.common.timing

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

/**
 * Wraps the handler invocation + response write in a
 * `personal_stack.request.handler` Observation. Same call site emits a
 * Micrometer Timer (per-URI percentile metric) and an OTel span (Tempo
 * waterfall row beneath the outer request span). Subtracting the
 * handler observation from the request observation in the dashboard
 * gives the filter-chain cost (Spring Security, session, CORS, …).
 */
class HandlerTimingInterceptor(
    private val observationRegistry: ObservationRegistry = ObservationRegistry.NOOP,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        request.setAttribute(RequestTimingAttributes.HANDLER_START_NANOS, System.nanoTime())
        val observation =
            Observation
                .createNotStarted(OBSERVATION_NAME, observationRegistry)
                .lowCardinalityKeyValue("method", request.method)
                .start()
        val scope = observation.openScope()
        request.setAttribute(OBSERVATION_REQUEST_KEY, observation)
        request.setAttribute(SCOPE_REQUEST_KEY, scope)
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val observation = request.getAttribute(OBSERVATION_REQUEST_KEY) as? Observation
        val scope = request.getAttribute(SCOPE_REQUEST_KEY) as? Observation.Scope
        val start = request.getAttribute(RequestTimingAttributes.HANDLER_START_NANOS) as? Long ?: return
        try {
            if (ex != null) observation?.error(ex)
            observation
                ?.lowCardinalityKeyValue("uri", uriTemplate(request))
                ?.lowCardinalityKeyValue("status", response.status.toString())
        } finally {
            scope?.close()
            observation?.stop()
        }
        val durationMs = (System.nanoTime() - start) / RequestTimingAttributes.NANOS_PER_MILLI
        logger.info(
            "[handler] {} {} duration_ms={}",
            request.method,
            request.requestURI,
            durationMs,
        )
    }

    private fun uriTemplate(request: HttpServletRequest): String {
        val matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
        return matched ?: "UNKNOWN"
    }

    companion object {
        const val OBSERVATION_NAME: String = "personal_stack.request.handler"
        private const val OBSERVATION_REQUEST_KEY = "personal-stack.timing.handler_observation"
        private const val SCOPE_REQUEST_KEY = "personal-stack.timing.handler_scope"
        private val logger = LoggerFactory.getLogger("com.jorisjonkers.personalstack.common.timing.handler")
    }
}
