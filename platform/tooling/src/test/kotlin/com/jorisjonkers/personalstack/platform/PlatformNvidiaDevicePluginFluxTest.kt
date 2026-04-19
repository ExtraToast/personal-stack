package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformNvidiaDevicePluginFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `core flux kustomization includes nvidia device plugin`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/core/kustomization.yaml").toFile().readText()

        assertThat(kustomization).contains("- nvidia-device-plugin")
    }

    @Test
    fun `nvidia device plugin is installed through the official helm chart on nvidia capable nodes`() {
        val kustomization =
            repositoryRoot.resolve("platform/cluster/flux/apps/core/nvidia-device-plugin/kustomization.yaml").toFile().readText()
        val namespace =
            repositoryRoot.resolve("platform/cluster/flux/apps/core/nvidia-device-plugin/namespace.yaml").toFile().readText()
        val source = repositoryRoot.resolve("platform/cluster/flux/apps/core/nvidia-device-plugin/source.yaml").toFile().readText()
        val release = repositoryRoot.resolve("platform/cluster/flux/apps/core/nvidia-device-plugin/release.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("namespace.yaml")
            .contains("source.yaml")
            .contains("release.yaml")

        assertThat(namespace)
            .contains("kind: Namespace")
            .contains("name: nvidia-device-plugin")

        assertThat(source)
            .contains("kind: HelmRepository")
            .contains("name: nvidia")
            .contains("url: https://nvidia.github.io/k8s-device-plugin")

        assertThat(release)
            .contains("kind: HelmRelease")
            .contains("name: nvidia-device-plugin")
            .contains("namespace: nvidia-device-plugin")
            .contains("chart: nvidia-device-plugin")
            .contains("compatWithCPUManager: true")
            .contains("personal-stack/capability-nvidia:")
            .contains("key: nvidia.com/gpu")
            .contains("operator: Exists")
            .contains("effect: NoSchedule")
            .contains("gfd:")
            .contains("enabled: false")
    }
}
