package com.jorisjonkers.personalstack.common.timing

/**
 * Request-attribute keys used by the timing instrumentation. Attributes
 * scope per HttpServletRequest (which is per virtual thread / per
 * request), so they survive bouncing between threads and are isolated
 * across concurrent requests.
 */
internal object RequestTimingAttributes {
    const val QUERY_COUNT = "personal-stack.timing.query_count"
    const val TOTAL_QUERY_NANOS = "personal-stack.timing.total_query_nanos"
    const val HANDLER_START_NANOS = "personal-stack.timing.handler_start_nanos"
    const val NANOS_PER_MILLI = 1_000_000L

    // Instants captured at each pipeline checkpoint, consumed by
    // RequestPipelineSpanFilter at request end to emit retroactive
    // child spans (security-chain, handler-dispatch, handler,
    // response-finalize) under the agent's SERVER span.
    const val REQUEST_START_INSTANT = "personal-stack.timing.pipeline.request_start"
    const val SECURITY_CHAIN_END_INSTANT = "personal-stack.timing.pipeline.security_chain_end"
    const val HANDLER_START_INSTANT = "personal-stack.timing.pipeline.handler_start"
    const val HANDLER_END_INSTANT = "personal-stack.timing.pipeline.handler_end"
}
