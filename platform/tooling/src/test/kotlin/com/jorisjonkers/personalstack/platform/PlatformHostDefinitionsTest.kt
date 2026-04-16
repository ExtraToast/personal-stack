package com.jorisjonkers.personalstack.platform

import com.jorisjonkers.personalstack.platform.inventory.PlatformFleetLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PlatformHostDefinitionsTest {
    private val repositoryRoot = RepositoryRootLocator().locate()
    private val fleet =
        PlatformFleetLoader().load(
            repositoryRoot.resolve("platform/inventory/fleet.yaml"),
        )

    @Test
    fun `every inventory node has a nix host definition and disk layout`() {
        fleet.nodes.keys.forEach { nodeName ->
            assertThat(Files.exists(repositoryRoot.resolve("platform/nix/hosts/${nodeName}/default.nix")))
                .describedAs("host %s should define a default.nix", nodeName)
                .isTrue()
            assertThat(Files.exists(repositoryRoot.resolve("platform/nix/hosts/${nodeName}/disko.nix")))
                .describedAs("host %s should define a disko.nix", nodeName)
                .isTrue()
        }
    }

    @Test
    fun `host definitions import profiles implied by fleet roles and capabilities`() {
        fleet.nodes.forEach { (nodeName, node) ->
            val hostDefinition = repositoryRoot.resolve("platform/nix/hosts/${nodeName}/default.nix").toFile().readText()

            if ("k3s-control-plane" in node.targetRoles) {
                assertThat(hostDefinition).contains("../../profiles/control-plane.nix")
            }
            if ("k3s-worker" in node.targetRoles) {
                assertThat(hostDefinition).contains("../../profiles/worker.nix")
            }
            if ("utility-host" in node.targetRoles) {
                assertThat(hostDefinition).contains("../../profiles/utility.nix")
            }
            if ("nvidia" in node.capabilities) {
                assertThat(hostDefinition).contains("../../profiles/gpu-nvidia.nix")
            }
        }
    }

    @Test
    fun `raspberry pi hosts override efi boot with generic extlinux`() {
        listOf("enschede-pi-1", "enschede-pi-2", "enschede-pi-3").forEach { nodeName ->
            val hostDefinition = repositoryRoot.resolve("platform/nix/hosts/${nodeName}/default.nix").toFile().readText()

            assertThat(hostDefinition)
                .contains("systemd-boot.enable = lib.mkForce false")
                .contains("efi.canTouchEfiVariables = lib.mkForce false")
                .contains("generic-extlinux-compatible.enable = true")
        }
    }

    @Test
    fun `flake exports every inventory node with the correct architecture`() {
        val flake = repositoryRoot.resolve("platform/flake.nix").toFile().readText()

        fleet.nodes.forEach { (nodeName, node) ->
            assertThat(flake).contains("${nodeName} = mkHost")

            val expectedSystem =
                when (node.arch) {
                    "amd64" -> "x86_64-linux"
                    "arm64" -> "aarch64-linux"
                    else -> error("Unsupported arch ${node.arch}")
                }
            assertThat(flake).contains("${nodeName} = mkHost \"${expectedSystem}\"")
        }
    }

    @Test
    fun `flake exports deploy targets for every ssh reachable inventory node`() {
        val flake = repositoryRoot.resolve("platform/flake.nix").toFile().readText()

        fleet.nodes.forEach { (nodeName, node) ->
            node.ssh?.let {
                val expectedSystem =
                    when (node.arch) {
                        "amd64" -> "x86_64-linux"
                        "arm64" -> "aarch64-linux"
                        else -> error("Unsupported arch ${node.arch}")
                    }

                assertThat(flake).contains("deploy.nodes.${nodeName}")
                assertThat(flake).contains("hostname = \"${it.host}\"")
                assertThat(flake).contains("sshUser = \"${it.user}\"")
                assertThat(flake).contains("sshOpts = [ \"-p\" \"${it.port}\" ]")
                assertThat(flake)
                    .contains("deploy-rs.lib.${expectedSystem}.activate.nixos self.nixosConfigurations.${nodeName}")
            }
        }
    }
}
