package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformObservabilityFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `production cluster kustomization includes observability apps`() {
        val clusterKustomization =
            repositoryRoot.resolve("platform/cluster/flux/clusters/production/kustomization.yaml").toFile().readText()
        val appKustomization =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/kustomization.yaml").toFile().readText()
        val namespace =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/namespace.yaml").toFile().readText()

        assertThat(clusterKustomization).contains("- ../../apps/observability")
        assertThat(appKustomization)
            .contains("namespace.yaml")
            .contains("- metrics-stack")
            .contains("- grafana")
            .contains("- loki")
            .contains("- tempo")
            .contains("- alloy")
            .contains("backup-jobs.yaml")
        assertThat(namespace)
            .contains("kind: Namespace")
            .contains("name: observability")
    }

    @Test
    fun `metrics stack installs kube prometheus stack with frankfurt pinned prometheus`() {
        val kustomization =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/metrics-stack/kustomization.yaml").toFile().readText()
        val source =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/metrics-stack/source.yaml").toFile().readText()
        val release =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/metrics-stack/release.yaml").toFile().readText()
        val alerts =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/metrics-stack/platform-alerts.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("source.yaml")
            .contains("release.yaml")
            .contains("platform-alerts.yaml")

        assertThat(source)
            .contains("kind: HelmRepository")
            .contains("name: prometheus-community")
            .contains("url: https://prometheus-community.github.io/helm-charts")

        assertThat(release)
            .contains("kind: HelmRelease")
            .contains("name: metrics-stack")
            .contains("namespace: observability")
            .contains("chart: kube-prometheus-stack")
            .contains("fullnameOverride: metrics-stack")
            .contains("grafana:")
            .contains("enabled: false")
            .contains("defaultRules:")
            .contains("create: true")
            .contains("prometheus:")
            .contains("prometheusSpec:")
            .contains("nodeSelector:")
            .contains("personal-stack/site: frankfurt")
            .contains("storageSpec:")
            .contains("alertmanager:")
            .contains("enabled: true")

        assertThat(alerts)
            .contains("kind: PrometheusRule")
            .contains("name: platform-alerts")
            .contains("PlatformIngressErrorsHigh")
            .contains("PlatformNodeDiskPressure")
    }

    @Test
    fun `grafana app provides datasources persistence and platform overview dashboard`() {
        val kustomization =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/grafana/kustomization.yaml").toFile().readText()
        val source =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/grafana/source.yaml").toFile().readText()
        val release =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/grafana/release.yaml").toFile().readText()
        val datasources =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/grafana/grafana-datasources.yaml").toFile().readText()
        val dashboard =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/grafana/platform-overview-dashboard.yaml").toFile().readText()

        assertThat(kustomization)
            .contains("source.yaml")
            .contains("release.yaml")
            .contains("grafana-datasources.yaml")
            .contains("platform-overview-dashboard.yaml")

        assertThat(source)
            .contains("kind: HelmRepository")
            .contains("name: grafana")
            .contains("url: https://grafana.github.io/helm-charts")

        assertThat(release)
            .contains("kind: HelmRelease")
            .contains("name: grafana")
            .contains("namespace: observability")
            .contains("chart: grafana")
            .contains("persistence:")
            .contains("enabled: true")
            .contains("nodeSelector:")
            .contains("personal-stack/site: frankfurt")
            .contains("sidecar:")
            .contains("dashboards:")
            .contains("datasources:")

        assertThat(datasources)
            .contains("kind: ConfigMap")
            .contains("name: grafana-datasources")
            .contains("http://metrics-stack-prometheus.observability.svc.cluster.local:9090")
            .contains("http://loki.observability.svc.cluster.local:3100")
            .contains("http://tempo.observability.svc.cluster.local:3200")

        assertThat(dashboard)
            .contains("kind: ConfigMap")
            .contains("name: platform-overview-dashboard")
            .contains("\"title\": \"Platform / Cluster Overview\"")
            .contains("sum(rate(container_cpu_usage_seconds_total")
            .contains("histogram_quantile")
    }

    @Test
    fun `logs traces and collectors are wired through loki tempo and alloy`() {
        val lokiRelease =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/loki/release.yaml").toFile().readText()
        val tempoRelease =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/tempo/release.yaml").toFile().readText()
        val alloyRelease =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/alloy/release.yaml").toFile().readText()
        val alloyConfig =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/alloy/config.yaml").toFile().readText()

        assertThat(lokiRelease)
            .contains("chart: loki")
            .contains("deploymentMode: SingleBinary")
            .contains("singleBinary:")
            .contains("persistence:")
            .contains("personal-stack/site: frankfurt")

        assertThat(tempoRelease)
            .contains("chart: tempo")
            .contains("persistence:")
            .contains("enabled: true")
            .contains("metricsGenerator:")
            .contains("remoteWriteUrl: http://metrics-stack-prometheus.observability.svc.cluster.local:9090/api/v1/write")

        assertThat(alloyRelease)
            .contains("chart: alloy")
            .contains("controller:")
            .contains("type: daemonset")
            .contains("configMap:")
            .contains("create: false")
            .contains("name: alloy-config")

        assertThat(alloyConfig)
            .contains("kind: ConfigMap")
            .contains("name: alloy-config")
            .contains("endpoint = \"http://tempo.observability.svc.cluster.local:4318\"")
            .contains("url = \"http://loki.observability.svc.cluster.local:3100/loki/api/v1/push\"")
            .contains("discovery.kubernetes")
            .contains("loki.source.kubernetes")
    }

    @Test
    fun `backup jobs define vault postgres rabbitmq and pvc snapshot schedules`() {
        val backups =
            repositoryRoot.resolve("platform/cluster/flux/apps/observability/backup-jobs.yaml").toFile().readText()

        assertThat(backups)
            .contains("kind: PersistentVolumeClaim")
            .contains("name: observability-backups")
            .contains("kind: CronJob")
            .contains("name: vault-raft-snapshot")
            .contains("name: postgres-logical-backup")
            .contains("name: rabbitmq-definitions-backup")
            .contains("name: pvc-snapshot-backup")
            .contains("vault-backup-token")
            .contains("postgres-backup")
            .contains("rabbitmq-backup")
            .contains("bitnami/kubectl")
            .contains("suspend: true")
    }
}
