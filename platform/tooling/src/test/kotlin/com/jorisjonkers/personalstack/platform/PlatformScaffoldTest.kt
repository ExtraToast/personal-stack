package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

class PlatformScaffoldTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `platform scaffold exists for nix and flux bootstrap`() {
        val requiredFiles =
            listOf(
                "platform/flake.nix",
                "platform/nix/profiles/control-plane.nix",
                "platform/nix/profiles/worker.nix",
                "platform/nix/profiles/utility.nix",
                "platform/nix/profiles/gpu-nvidia.nix",
                "platform/nix/hosts/frankfurt-contabo-1/default.nix",
                "platform/nix/hosts/frankfurt-contabo-1/disko.nix",
                "platform/cluster/flux/clusters/production/kustomization.yaml",
                "platform/cluster/bootstrap/README.md",
            )

        assertThat(requiredFiles)
            .allSatisfy { file ->
                assertThat(Files.exists(repositoryRoot.resolve(file)))
                    .describedAs("%s should exist", file)
                    .isTrue()
            }
    }

    @Test
    fun `flake wires required platform inputs`() {
        val flake = repositoryRoot.resolve("platform/flake.nix").readText()

        assertThat(flake).contains("deploy-rs")
        assertThat(flake).contains("disko")
        assertThat(flake).contains("nixos-anywhere")
    }

    @Test
    fun `flux production kustomization references core apps`() {
        val kustomization =
            repositoryRoot.resolve("platform/cluster/flux/clusters/production/kustomization.yaml").readText()

        assertThat(kustomization).contains("../../apps/core")
    }
}
