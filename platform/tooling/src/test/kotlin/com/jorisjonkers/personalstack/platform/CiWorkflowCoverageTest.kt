package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Meta-regression test for the class of bug where a new Kotlin service
 * module is added to `settings.gradle.kts` but never wired into the
 * CI workflow path filters. CI ran green on its way to `main`, but
 * `.github/workflows/build-and-publish.yml` never built the image,
 * so Keel + Flux pulled the manifest, hit `ImagePullBackOff` on
 * `ghcr.io/.../<service>:latest`, and the pod stayed offline
 * indefinitely.
 *
 * For every `include(":services:<name>")` line in `settings.gradle.kts`
 * that has a corresponding `services/<name>/Dockerfile`, the three
 * Kotlin-service workflows must declare a path filter for it. UI-only
 * services live elsewhere (auth-ui, assistant-ui, app-ui) and use
 * different build paths; we only enforce coverage for JVM services
 * the workflows actually ship as ghcr images.
 */
class CiWorkflowCoverageTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `every kotlin service module is wired into build-and-publish, fast, and full workflows`() {
        val services = collectShippedKotlinServices()
        assertThat(services)
            .describedAs("expected to find at least one :services:<svc> with a Dockerfile in settings.gradle.kts")
            .isNotEmpty

        val workflows =
            listOf(
                ".github/workflows/build-and-publish.yml",
                ".github/workflows/fast.yml",
                ".github/workflows/full.yml",
            ).associateWith { repositoryRoot.resolve(it).toFile().readText() }

        val missing =
            workflows.flatMap { (path, body) ->
                services
                    .filterNot { svc -> body.contains("services/$svc/**") }
                    .map { svc -> "$path :: $svc" }
            }
        assertThat(missing)
            .describedAs(
                "One or more Kotlin services declared in settings.gradle.kts and shipping a Dockerfile " +
                    "are not referenced by a path filter in the listed CI workflow. The Build & Publish " +
                    "matrix won't include them, so `ghcr.io/.../<svc>:latest` is never produced and the " +
                    "Deployment stays in ImagePullBackOff. Add `<svc>: ['services/<svc>/**']` to the " +
                    "workflow's `filters:` block (and a corresponding line in the rebuild matrix).\n" +
                    "Missing: $missing",
            ).isEmpty()
    }

    private fun collectShippedKotlinServices(): List<String> {
        val settings = repositoryRoot.resolve("settings.gradle.kts").toFile().readText()
        return Regex("""include\("(:services:[^"]+)"\)""")
            .findAll(settings)
            .map { it.groupValues[1].removePrefix(":services:") }
            .filter { svc ->
                repositoryRoot.resolve("services/$svc/Dockerfile").toFile().exists()
            }.toList()
    }
}
