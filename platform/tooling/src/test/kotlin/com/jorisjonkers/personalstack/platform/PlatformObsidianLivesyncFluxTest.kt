package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression test for the silent CouchDB crashloop that landed when
 * the `obsidian-livesync` StatefulSet was first deployed.
 *
 * The official `couchdb:3.4` image runs as uid 0 by default and its
 * `docker-entrypoint.sh` then chowns the entire `/opt/couchdb` tree:
 *
 *     find /opt/couchdb \! \( -user couchdb -group couchdb \) \
 *       -exec chown -f couchdb:couchdb {} +
 *
 * The configmap snippets we mount as `subPath: cors.ini` /
 * `single-node.ini` are read-only bind mounts from the kubelet's
 * projected volume — `chown -f` suppresses the error *message* but
 * still exits 1, find aggregates that into a non-zero exit, the
 * entrypoint's `set -e` trips, and the container terminates with
 * exit 1 *before* CouchDB writes a single log line. The pod
 * crashlooped with empty logs and a `Startup probe failed: connect:
 * connection refused` event masking the real cause.
 *
 * Pinning `runAsUser: 5984` skips the root-only chown block in the
 * entrypoint. Without this assertion the regression has no test
 * surface — every prior crash was caught only by manually running
 * `bash -x docker-entrypoint.sh` inside an inspect pod.
 */
class PlatformObsidianLivesyncFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `couchdb container pins runAsUser to 5984 so the entrypoint skips the chown that fails on subPath configmap mounts`() {
        val manifest =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/knowledge/obsidian-livesync/statefulset.yaml")
                .toFile()
                .readText()

        assertThat(manifest)
            .contains("kind: StatefulSet")
            .contains("name: obsidian-livesync")
            .contains("namespace: knowledge-system")
            .contains("image: couchdb:3.4")
            // configmap subPath mounts that trigger the chown failure
            .contains("subPath: cors.ini")
            .contains("subPath: single-node.ini")
            // fsGroup keeps the local-path PVC writable by uid 5984
            .contains("fsGroup: 5984")
            // The fix: container-level runAsUser/runAsGroup. The pod-level
            // securityContext only sets fsGroup, so the container still
            // starts as root unless this is set explicitly.
            .contains("runAsUser: 5984")
            .contains("runAsGroup: 5984")
    }
}
