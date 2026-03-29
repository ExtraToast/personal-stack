import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

tasks.withType<Test>().configureEach {
    testLogging {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR,
        )
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        showStandardStreams = true
    }

    afterSuite(
        KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
            if (desc.parent == null) {
                val output = buildString {
                    append("Results: ${result.resultType}")
                    append(" (${result.testCount} tests")
                    append(", ${result.successfulTestCount} passed")
                    append(", ${result.failedTestCount} failed")
                    append(", ${result.skippedTestCount} skipped)")
                }
                println("\n$output")
            }
        }),
    )
}
