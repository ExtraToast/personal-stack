package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformLanIngressFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `core flux kustomization includes metallb and lan ingress controller`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/core/kustomization.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("- metallb")
            .contains("- lan-ingress-controller")
    }

    @Test
    fun `metallb flux app advertises the enschede lan ingress vip`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/core/metallb/kustomization.yaml").toFile().readText()
        val namespace = repositoryRoot.resolve("platform/cluster/flux/apps/core/metallb/namespace.yaml").toFile().readText()
        val source = repositoryRoot.resolve("platform/cluster/flux/apps/core/metallb/source.yaml").toFile().readText()
        val release = repositoryRoot.resolve("platform/cluster/flux/apps/core/metallb/release.yaml").toFile().readText()
        val config = repositoryRoot.resolve("platform/cluster/flux/apps/core/metallb/config.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("namespace.yaml")
            .contains("source.yaml")
            .contains("release.yaml")
            .contains("config.yaml")

        assertThat(namespace)
            .contains("kind: Namespace")
            .contains("name: metallb-system")

        assertThat(source)
            .contains("kind: HelmRepository")
            .contains("name: metallb")
            .contains("url: https://metallb.github.io/metallb")

        assertThat(release)
            .contains("kind: HelmRelease")
            .contains("name: metallb")
            .contains("namespace: metallb-system")
            .contains("chart: metallb")

        assertThat(config)
            .contains("kind: IPAddressPool")
            .contains("name: enschede-lan")
            .contains("- 192.168.1.240-192.168.1.240")
            .contains("kind: L2Advertisement")
            .contains("ipAddressPools:")
            .contains("- enschede-lan")
    }

    @Test
    fun `lan ingress controller is pinned to lan ingress nodes and uses the metallb vip`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/core/lan-ingress-controller/kustomization.yaml").toFile().readText()
        val namespace = repositoryRoot.resolve("platform/cluster/flux/apps/core/lan-ingress-controller/namespace.yaml").toFile().readText()
        val source = repositoryRoot.resolve("platform/cluster/flux/apps/core/lan-ingress-controller/source.yaml").toFile().readText()
        val release = repositoryRoot.resolve("platform/cluster/flux/apps/core/lan-ingress-controller/release.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("namespace.yaml")
            .contains("source.yaml")
            .contains("release.yaml")

        assertThat(namespace)
            .contains("kind: Namespace")
            .contains("name: ingress-lan-system")

        assertThat(source)
            .contains("kind: HelmRepository")
            .contains("name: traefik")

        assertThat(release)
            .contains("kind: HelmRelease")
            .contains("name: traefik-lan")
            .contains("namespace: ingress-lan-system")
            .contains("name: traefik-lan")
            .contains("ingressClass:")
            .contains("isDefaultClass: false")
            .contains("kubernetesCRD:")
            .contains("ingressClass: traefik-lan")
            .contains("kubernetesIngress:")
            .contains("nodeSelector:")
            .contains("personal-stack/capability-lan-ingress: \"true\"")
            .contains("externalTrafficPolicy: Local")
            .contains("metallb.io/address-pool: enschede-lan")
            .contains("metallb.io/loadBalancerIPs: 192.168.1.240")
    }

    @Test
    fun `edge app publishes lan ingress routes for dual exposed services`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/edge/kustomization.yaml").toFile().readText()
        val routes = repositoryRoot.resolve("platform/cluster/flux/apps/edge/traefik-lan-ingressroutes.yaml").toFile().readText()

        assertThat(kustomization).contains("traefik-lan-ingressroutes.yaml")

        assertThat(routes)
            .contains("name: jellyfin-lan")
            .contains("name: radarr-lan")
            .contains("name: sonarr-lan")
            .contains("Host(`jellyfin.jorisjonkers.dev`)")
            .contains("Host(`radarr.jorisjonkers.dev`)")
            .contains("Host(`sonarr.jorisjonkers.dev`)")
            .contains("kubernetes.io/ingress.class: traefik-lan")
            .doesNotContain("external-dns.alpha.kubernetes.io/target")
    }
}
