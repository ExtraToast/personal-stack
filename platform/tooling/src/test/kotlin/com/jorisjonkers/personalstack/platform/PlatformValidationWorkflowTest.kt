package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PlatformValidationWorkflowTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `platform validation scripts exist`() {
        val requiredFiles =
            listOf(
                "platform/scripts/validate/render-platform.sh",
                "platform/scripts/validate/render-flux.sh",
                ".github/workflows/platform-validate.yml",
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
        assertThat(script).contains("kustomize build")
        assertThat(script).contains("helm template")
        assertThat(script).contains("kubeconform")
    }

    @Test
    fun `workflow delegates validation to platform script`() {
        val workflow = repositoryRoot.resolve(".github/workflows/platform-validate.yml").toFile().readText()

        assertThat(workflow).contains("platform/scripts/validate/render-platform.sh")
        assertThat(workflow).contains("platform/**")
    }
}
