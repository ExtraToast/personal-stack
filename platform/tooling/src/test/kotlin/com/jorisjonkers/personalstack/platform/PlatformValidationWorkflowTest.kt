package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PlatformValidationWorkflowTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `platform validation scripts exist`() {
        // The standalone platform-validate.yml workflow was folded into
        // .github/workflows/full.yml in #284 so its success could be a
        // required signal for merge; this test now asserts the script
        // files plus the full-pipeline workflow that runs them.
        val requiredFiles =
            listOf(
                "platform/scripts/render/render-edge-route-catalog-configmap.sh",
                "platform/scripts/render/render-traefik-ingressroutes.sh",
                "platform/scripts/validate/render-platform.sh",
                "platform/scripts/validate/render-flux.sh",
                ".github/workflows/full.yml",
            )

        assertThat(requiredFiles)
            .allSatisfy { file ->
                assertThat(Files.exists(repositoryRoot.resolve(file)))
                    .describedAs("%s should exist", file)
                    .isTrue()
            }
    }

    @Test
    fun `render script runs nix flux helm and kubeconform checks`() {
        val script = repositoryRoot.resolve("platform/scripts/validate/render-platform.sh").toFile().readText()

        assertThat(script).contains("nix flake check")
        assertThat(script).contains("render-edge-catalog-configmap.sh")
        assertThat(script).contains("render-edge-route-catalog-configmap.sh")
        assertThat(script).contains("render-traefik-ingressroutes.sh")
        assertThat(script).contains("git diff --exit-code -- platform/cluster/flux/apps/edge/edge-catalog-configmap.yaml")
        assertThat(script).contains("git diff --exit-code -- platform/cluster/flux/apps/edge/edge-route-catalog-configmap.yaml")
        assertThat(script).contains("git diff --exit-code -- platform/cluster/flux/apps/edge/traefik-ingressroutes.yaml")
        assertThat(script).contains("kustomize build")
        assertThat(script).contains("helm template")
        assertThat(script).contains("kubeconform")
    }

    @Test
    fun `full pipeline workflow runs platform validation on platform changes`() {
        // The platform-validate job lives in full.yml as of #284. It
        // gates on the detect-changes job's `any-platform` output, so
        // we assert both that the job exists and that the path filter
        // it depends on covers `platform/**`.
        val workflow = repositoryRoot.resolve(".github/workflows/full.yml").toFile().readText()

        assertThat(workflow).contains("platform-validate:")
        assertThat(workflow).contains("platform/scripts/validate/render-platform.sh")
        assertThat(workflow).contains("platform/**")
    }
}
