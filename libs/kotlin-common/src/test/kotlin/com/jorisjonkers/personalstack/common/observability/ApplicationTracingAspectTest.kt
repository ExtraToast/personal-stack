package com.jorisjonkers.personalstack.common.observability

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory

class ApplicationTracingAspectTest {
    private fun fixture(): Pair<RecordingHandler, TracedService> {
        val recorder = RecordingHandler()
        val registry =
            ObservationRegistry.create().apply {
                observationConfig().observationHandler(recorder)
            }
        val aspect = ApplicationTracingAspect(registry)
        val target = TracedService()
        val factory =
            AspectJProxyFactory(target).apply {
                addAspect(aspect)
            }
        return recorder to factory.getProxy()
    }

    @Test
    fun `creates a span named ClassName_method`() {
        val (recorder, proxy) = fixture()
        proxy.greet("world")
        assertThat(recorder.observations.map { it.name }).containsExactly("TracedService.greet")
    }

    @Test
    fun `records errors when the method throws`() {
        val (recorder, proxy) = fixture()
        runCatching { proxy.boom() }
        val rec = recorder.observations.single()
        assertThat(rec.name).isEqualTo("TracedService.boom")
        assertThat(rec.error).isInstanceOf(IllegalStateException::class.java)
    }

    @Suppress("FunctionOnlyReturningConstant")
    open class TracedService {
        open fun greet(name: String): String = "hello $name"

        open fun boom(): Nothing = throw IllegalStateException("nope")
    }

    private data class Recorded(
        val name: String,
        val error: Throwable?,
    )

    private class RecordingHandler : ObservationHandler<Observation.Context> {
        val observations = mutableListOf<Recorded>()
        private var lastError: Throwable? = null

        override fun supportsContext(context: Observation.Context): Boolean = true

        override fun onError(context: Observation.Context) {
            lastError = context.error
        }

        override fun onStop(context: Observation.Context) {
            observations.add(Recorded(context.name ?: "<unnamed>", lastError))
            lastError = null
        }
    }
}
