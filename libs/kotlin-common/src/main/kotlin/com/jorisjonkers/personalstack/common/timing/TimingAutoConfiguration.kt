package com.jorisjonkers.personalstack.common.timing

import io.micrometer.observation.ObservationRegistry
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
 * service. Each call site records via Spring's Observation API, so the
 * same hot path emits a Micrometer Timer (Prometheus histogram for the
 * burndown dashboards) AND, when a tracer is on the classpath, an OTel
 * span that nests into the running trace.
 *
 *  - `RequestTimingFilter` — `personal_stack.request` span around the
 *    entire filter chain. Tags: method, uri-template, status, outcome.
 *  - `HandlerTimingInterceptor` — `personal_stack.request.handler` span
 *    around the controller invocation + response write. Subtract from
 *    the request span to get the filter-chain cost.
 *  - `JooqQueryTimingListener` — `personal_stack.db.query` span per
 *    executed statement, tagged by jOOQ `type` (SELECT/INSERT/…).
 *
 * Spring Boot auto-creates an `ObservationRegistry` bean whenever the
 * Observation API is on the classpath; when it isn't, the framework's
 * default fallback hands back `ObservationRegistry.NOOP`. Either way
 * the timing beans degrade gracefully.
 *
 * Gated on `personal-stack.timing.enabled` (default true). Flip to
 * `false` in any service's `application*.yml` to disable the entire
 * instrumentation after diagnosis.
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
    fun requestTimingFilter(observationRegistry: ObservationRegistry): RequestTimingFilter =
        RequestTimingFilter(observationRegistry)

    @Bean
    @ConditionalOnClass(DefaultExecuteListenerProvider::class)
    fun jooqTimingExecuteListenerProvider(observationRegistry: ObservationRegistry): DefaultExecuteListenerProvider =
        DefaultExecuteListenerProvider(JooqQueryTimingListener(observationRegistry))

    @Bean
    @ConditionalOnClass(WebMvcConfigurer::class)
    fun handlerTimingWebMvcConfigurer(observationRegistry: ObservationRegistry): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(HandlerTimingInterceptor(observationRegistry))
            }
        }
}
