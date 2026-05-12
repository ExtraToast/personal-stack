package com.jorisjonkers.personalstack.common.timing

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.jooq.impl.DefaultExecuteListenerProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Auto-registers the timing instrumentation into any Spring Boot
 * service on the classpath. Three coordinated logs:
 *
 *  - `RequestTimingFilter` — total request time, plus per-request
 *    SQL count + total SQL time accumulated by the jOOQ listener.
 *  - `JooqQueryTimingListener` — one line per query with type,
 *    duration, and the first 200 chars of SQL (to spot slow queries
 *    and N+1 patterns).
 *  - `HandlerTimingInterceptor` — handler + serialization time,
 *    so we can subtract from the request total to get the filter
 *    chain (Spring Security, session, CORS) cost.
 *
 * Gated on `personal-stack.timing.enabled` (default true). Flip to
 * `false` in any service's `application*.yml` to silence the timing
 * logs after diagnosis.
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "personal-stack.timing",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class TimingAutoConfiguration {
    @Bean
    @ConditionalOnClass(OncePerRequestFilter::class)
    fun requestTimingFilter(): RequestTimingFilter = RequestTimingFilter()

    @Bean
    @ConditionalOnClass(DefaultExecuteListenerProvider::class)
    fun jooqTimingExecuteListenerProvider(): DefaultExecuteListenerProvider =
        DefaultExecuteListenerProvider(JooqQueryTimingListener())

    @Bean
    @ConditionalOnClass(WebMvcConfigurer::class)
    fun handlerTimingWebMvcConfigurer(): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(HandlerTimingInterceptor())
            }
        }

    @Bean
    @ConditionalOnClass(OncePerRequestFilter::class, Tracer::class)
    fun securityChainBoundaryFilter(): SecurityChainBoundaryFilter = SecurityChainBoundaryFilter()

    @Bean
    @ConditionalOnClass(OncePerRequestFilter::class, Tracer::class)
    fun requestPipelineSpanFilter(): RequestPipelineSpanFilter =
        RequestPipelineSpanFilter(GlobalOpenTelemetry.getTracer(PIPELINE_INSTRUMENTATION_SCOPE))

    private companion object {
        const val PIPELINE_INSTRUMENTATION_SCOPE =
            "com.jorisjonkers.personalstack.timing.pipeline"
    }
}
