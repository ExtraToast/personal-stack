package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PlatformScaffoldTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    // Smoke-test that the expected scaffold is present so a deletion of a
    // load-bearing file fails CI before it reaches an install host. We
    // deliberately do NOT grep these files for substrings — that's a
    // rename-sensitive test that only catches spelling. `nix flake check`
    // catches real Nix breakage; the deploy-rs/install scripts have their
    // own behavior tests in PlatformDeployScriptsTest.
    @Test
    fun `platform scaffold exists for nix and flux bootstrap`() {
        val requiredFiles =
            listOf(
                "platform/flake.nix",
                "platform/nix/profiles/control-plane.nix",
                "platform/nix/profiles/worker.nix",
                "platform/nix/profiles/utility.nix",
                "platform/nix/profiles/gpu-nvidia.nix",
                "platform/nix/modules/base/default.nix",
                "platform/nix/modules/services/game-streaming.nix",
                "platform/nix/authorized-keys/README.md",
                "platform/nix/modules/image/raspberry-pi-sd-image.nix",
                "platform/nix/hosts/frankfurt-contabo-1/default.nix",
                "platform/nix/hosts/frankfurt-contabo-1/disko.nix",
                "platform/scripts/install/install-host.sh",
                "platform/scripts/deploy/deploy-host.sh",
                "platform/scripts/bootstrap/bootstrap-tailnet.sh",
                "platform/scripts/bootstrap/bootstrap-k3s-worker.sh",
                "platform/scripts/build/build-pi-image.sh",
                "platform/cluster/bootstrap/game-streaming-playbook.md",
                "platform/cluster/flux/clusters/production/kustomization.yaml",
            )

        assertThat(requiredFiles)
            .allSatisfy { file ->
                assertThat(Files.exists(repositoryRoot.resolve(file)))
                    .describedAs("%s should exist", file)
                    .isTrue()
            }
    }
}
