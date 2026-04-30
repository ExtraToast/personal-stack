package com.jorisjonkers.personalstack.common.observability

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature

/**
 * Logs duration of every Spring-bean method on @RestController,
 * @Service, and @Repository classes inside the project package. Ignored
 * for framework internals to avoid noise.
 */
@Aspect
class MethodTimingAspect(
    private val properties: TimingProperties,
) {
    @Around(
        "(@within(org.springframework.web.bind.annotation.RestController) || " +
            "@within(org.springframework.stereotype.Service) || " +
            "@within(org.springframework.stereotype.Repository))",
    )
    fun timeMethod(joinPoint: ProceedingJoinPoint): Any? {
        val target = joinPoint.target?.javaClass ?: return joinPoint.proceed()
        if (!target.name.startsWith(PROJECT_PACKAGE)) return joinPoint.proceed()
        val signature = joinPoint.signature as? MethodSignature
        val methodName = signature?.method?.name ?: "unknown"
        val startNs = System.nanoTime()
        try {
            return joinPoint.proceed()
        } finally {
            val durationMs = (System.nanoTime() - startNs) / NS_PER_MS
            val slow = durationMs >= properties.slowMethodMs
            TimingLog.emit(
                slow = slow,
                kind = "method",
                durationMs = durationMs,
                kvs =
                    mapOf(
                        "target" to "${target.simpleName}#$methodName",
                    ),
            )
        }
    }

    companion object {
        private const val PROJECT_PACKAGE = "com.jorisjonkers.personalstack."
    }
}
