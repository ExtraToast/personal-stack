package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformGpuNvidiaRoleTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `gpu nvidia profile allows unfree nvidia packages`() {
        val profile = repositoryRoot.resolve("platform/nix/profiles/gpu-nvidia.nix").toFile().readText()

        assertThat(profile)
            .contains("nixpkgs.config.allowUnfreePredicate")
            .contains("lib.hasPrefix \"nvidia-\" name")
    }

    @Test
    fun `gpu nvidia role enables driver modesetting and container toolkit`() {
        val role = repositoryRoot.resolve("platform/nix/modules/roles/gpu-nvidia.nix").toFile().readText()

        assertThat(role)
            .contains("services.xserver.videoDrivers = [ \"nvidia\" ];")
            .contains("hardware.graphics.enable = true;")
            .contains("hardware.nvidia = {")
            .contains("open = false;")
            .contains("modesetting.enable = true;")
            .contains("package = config.boot.kernelPackages.nvidiaPackages.stable;")
            .contains("hardware.nvidia-container-toolkit.enable = true;")
            .contains("libva")
            .contains("pciutils")
    }
}
