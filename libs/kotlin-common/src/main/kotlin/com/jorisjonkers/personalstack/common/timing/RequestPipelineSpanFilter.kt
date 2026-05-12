package com.jorisjonkers.personalstack.common.timing

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

/**
 * Outer filter ordered before everything else. Collaborates with
 * `SecurityChainBoundaryFilter` (post Spring Security) and
 * `HandlerTimingInterceptor` (around the controller) to record four
 * `Instant` checkpoints per request, then emits up to four retroactive
 * child spans under the OTel agent's SERVER span:
 *
 *  - `pipeline.security-chain`       request start → security chain end
 *  - `pipeline.handler-dispatch`     security chain end → handler start
 *  - `pipeline.handler`              handler start → handler end
 *  - `pipeline.response-finalize`    handler end → request end
 *
 * Span emission uses `Span.spanBuilder.setStartTimestamp/end(Instant)`
 * so the spans line up exactly with where time was actually spent,
 * not when this filter happens to call `startSpan`. Parent is taken
 * from `Context.current()` at the end of the chain — by that point
 * the agent's SERVER span is the current span, so the new children
 * attach to the right trace.
 *
 * Spans are only emitted when both endpoints of a segment are
 * available, so 401 short-circuits inside Spring Security skip the
 * later spans gracefully.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RequestPipelineSpanFilter(
    private val tracer: Tracer,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestStart = Instant.now()
        request.setAttribute(RequestTimingAttributes.REQUEST_START_INSTANT, requestStart)
        try {
            filterChain.doFilter(request, response)
        } finally {
            emitPipelineSpans(request, requestStart, Instant.now())
        }
    }

    private fun emitPipelineSpans(
        request: HttpServletRequest,
        requestStart: Instant,
        requestEnd: Instant,
    ) {
        val securityChainEnd =
            request.getAttribute(RequestTimingAttributes.SECURITY_CHAIN_END_INSTANT) as? Instant
        val handlerStart =
            request.getAttribute(RequestTimingAttributes.HANDLER_START_INSTANT) as? Instant
        val handlerEnd =
            request.getAttribute(RequestTimingAttributes.HANDLER_END_INSTANT) as? Instant
        val parent = Context.current()

        emitSpan("pipeline.security-chain", requestStart, securityChainEnd, parent)
        emitSpan("pipeline.handler-dispatch", securityChainEnd, handlerStart, parent)
        emitSpan("pipeline.handler", handlerStart, handlerEnd, parent)
        emitSpan("pipeline.response-finalize", handlerEnd, requestEnd, parent)
    }

    private fun emitSpan(
        name: String,
        start: Instant?,
        end: Instant?,
        parent: Context,
    ) {
        if (start == null || end == null || !end.isAfter(start)) return
        val span: Span =
            tracer
                .spanBuilder(name)
                .setParent(parent)
                .setStartTimestamp(start)
                .startSpan()
        span.end(end)
    }
}
