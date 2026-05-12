package com.jorisjonkers.personalstack.common.observability

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean

// Wraps every method on every Spring bean under
// com.jorisjonkers.personalstack.** with a Micrometer observation.
// Bridged to OTel via `micrometer-tracing-bridge-otel` already on
// every service's runtime classpath, so each method becomes a span
// in Tempo with class-qualified name (e.g. `JooqUserRepository.findById`).
//
// The pointcut is deliberately bound to our package only: we don't
// want spans for Spring's filter chain, Lettuce commands, Jackson
// serialisation, etc. — only code we own and can fix.
//
// Configuration classes are excluded because their methods run at
// startup, never on the request path. Tracing helpers and the aspect
// itself are also excluded to avoid recursion.
//
// Bean is only registered when ObservationRegistry is on the
// classpath (i.e. when Spring Boot's actuator + observation stack
// are active). For non-Spring consumers of kotlin-common this is a
// no-op.
@AutoConfiguration
@ConditionalOnClass(ObservationRegistry::class, Aspect::class)
class ApplicationTracingAspectAutoConfiguration {
    @Bean
    fun applicationTracingAspect(registry: ObservationRegistry): ApplicationTracingAspect =
        ApplicationTracingAspect(registry)
}

@Aspect
class ApplicationTracingAspect(
    private val registry: ObservationRegistry,
) {
    // Catching Throwable is correct for an AOP @Around — the wrapped
    // call can throw anything and the span must record + re-throw it.
    @Suppress("TooGenericExceptionCaught")
    @Around(POINTCUT)
    fun trace(pjp: ProceedingJoinPoint): Any? {
        val signature = pjp.signature as? MethodSignature
        val className = signature?.declaringType?.simpleName ?: pjp.target?.javaClass?.simpleName ?: "Unknown"
        val methodName = signature?.name ?: pjp.signature.name
        val spanName = "$className.$methodName"
        val observation = Observation.start(spanName, registry)
        return try {
            observation.openScope().use { pjp.proceed() }
        } catch (t: Throwable) {
            observation.error(t)
            throw t
        } finally {
            observation.stop()
        }
    }

    private companion object {
        // Targets Spring stereotype-annotated classes in our packages.
        // kotlin-spring's allopen plugin opens these by default, which
        // means CGLIB can proxy them; non-stereotyped @Bean factories
        // (RequestTimingFilter, JooqQueryTimingListener, ...) remain
        // Kotlin-final and would fail CGLIB.
        //
        // Restricting to stereotypes is also semantically right: those
        // are our *business* beans — controllers, services, repositories
        // — which is exactly the layer we want to find slowness in.
        // Configuration classes are filtered out (only run at startup).
        //
        // The aspect's own classes are excluded by FQN to prevent
        // recursion; the test source (also in observability package)
        // is *not* excluded so the unit test can drive a proxy.
        private const val OBS_PKG = "com.jorisjonkers.personalstack.common.observability"
        private const val POINTCUT =
            "execution(* com.jorisjonkers.personalstack..*.*(..)) " +
                "&& (" +
                "@within(org.springframework.stereotype.Component) " +
                "|| @within(org.springframework.stereotype.Service) " +
                "|| @within(org.springframework.stereotype.Repository) " +
                "|| @within(org.springframework.stereotype.Controller) " +
                "|| @within(org.springframework.web.bind.annotation.RestController) " +
                "|| @within(org.springframework.web.bind.annotation.RestControllerAdvice) " +
                "|| @within(org.springframework.web.bind.annotation.ControllerAdvice) " +
                ") " +
                "&& !@within(org.springframework.context.annotation.Configuration) " +
                "&& !within($OBS_PKG.ApplicationTracingAspect) " +
                "&& !within($OBS_PKG.ApplicationTracingAspectAutoConfiguration) " +
                "&& !within($OBS_PKG.ActuatorObservationFilterAutoConfiguration) " +
                "&& !within($OBS_PKG.ActuatorObservationPredicate)"
    }
}
