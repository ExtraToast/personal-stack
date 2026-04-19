package com.jorisjonkers.personalstack.platform

import com.jorisjonkers.personalstack.platform.inventory.PlatformFleetLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformK3sNodeLabelsTest {
    private val repositoryRoot = RepositoryRootLocator().locate()
    private val fleet =
        PlatformFleetLoader().load(
            repositoryRoot.resolve("platform/inventory/fleet.yaml"),
        )

    @Test
    fun `k3s host definitions expose inventory derived node labels`() {
        fleet.nodes.forEach { (nodeName, node) ->
            val hasK3sRole = node.targetRoles.any { it.startsWith("k3s-") }
            if (!hasK3sRole) {
                return@forEach
            }

            val hostDefinition = repositoryRoot.resolve("platform/nix/hosts/${nodeName}/default.nix").toFile().readText()

            assertThat(hostDefinition).contains("personalStack.k3sNodeLabels")
            assertThat(hostDefinition).contains("\"personal-stack/site\" = \"${node.site}\"")
            assertThat(hostDefinition).contains("\"personal-stack/node\" = \"${nodeName}\"")
            assertThat(hostDefinition).contains("\"topology.kubernetes.io/region\" = \"${node.site}\"")

            node.targetRoles.forEach { role ->
                assertThat(hostDefinition).contains("\"personal-stack/role-${role}\" = \"true\"")
            }

            node.capabilities.forEach { capability ->
                assertThat(hostDefinition).contains("\"personal-stack/capability-${capability}\" = \"true\"")
            }

            node.gpus.forEach { gpu ->
                assertThat(hostDefinition).contains("\"personal-stack/gpu-vendor-${gpu.vendor}\" = \"true\"")
                assertThat(hostDefinition).contains("\"personal-stack/gpu-model-${gpu.model}\" = \"true\"")
                assertThat(hostDefinition).contains("\"personal-stack/gpu-class-${gpu.`class`}\" = \"true\"")
            }
        }
    }
}
