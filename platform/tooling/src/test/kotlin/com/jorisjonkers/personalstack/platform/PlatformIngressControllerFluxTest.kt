package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PlatformIngressControllerFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `core flux kustomization includes the ingress controller app`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/core/kustomization.yaml").toFile().readText()

        assertThat(kustomization).contains("- ingress-controller")
    }

    @Test
    fun `ingress controller flux app installs traefik through helm`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/core/ingress-controller/kustomization.yaml").toFile().readText()
        val namespace = repositoryRoot.resolve("platform/cluster/flux/apps/core/ingress-controller/namespace.yaml").toFile().readText()
        val source = repositoryRoot.resolve("platform/cluster/flux/apps/core/ingress-controller/source.yaml").toFile().readText()
        val release = repositoryRoot.resolve("platform/cluster/flux/apps/core/ingress-controller/release.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("namespace.yaml")
            .contains("source.yaml")
            .contains("release.yaml")

        assertThat(namespace)
            .contains("kind: Namespace")
            .contains("name: ingress-system")

        assertThat(source)
            .contains("kind: HelmRepository")
            .contains("name: traefik")
            .contains("url: https://traefik.github.io/charts")

        assertThat(release)
            .contains("kind: HelmRelease")
            .contains("name: traefik")
            .contains("namespace: ingress-system")
            .contains("chart: traefik")
            .contains("kubernetesCRD:")
            .contains("enabled: true")
            .contains("kubernetesIngress:")
            .contains("publishedService:")
            .contains("enabled: true")
    }

    @Test
    fun `edge app publishes a shared traefik forward auth middleware`() {
        val middlewarePath = repositoryRoot.resolve("platform/cluster/flux/apps/edge/traefik-forward-auth-middleware.yaml")

        assertThat(Files.exists(middlewarePath)).isTrue()

        val middleware = middlewarePath.toFile().readText()

        assertThat(middleware)
            .contains("kind: Middleware")
            .contains("name: forward-auth")
            .contains("namespace: edge-system")
            .contains("address: http://auth-api.auth-system.svc.cluster.local:8081/api/v1/auth/verify")
            .contains("trustForwardHeader: true")
            .contains("authResponseHeaders:")
            .contains("- X-User-Id")
            .contains("- X-User-Roles")
    }
}
