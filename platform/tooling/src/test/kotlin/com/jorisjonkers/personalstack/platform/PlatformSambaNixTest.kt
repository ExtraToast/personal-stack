package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformSambaNixTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `samba exposes role-scoped media shares with a dedicated all-access identity`() {
        val samba = repositoryRoot.resolve("platform/nix/modules/services/samba.nix").toFile().readText()
        val mediaStorage = repositoryRoot.resolve("platform/nix/modules/services/media-storage.nix").toFile().readText()

        assertThat(samba)
            .contains("users.users")
            .contains("\"media-root\"")
            .contains("\"media-downloads\"")
            .contains("\"media-tv\"")
            .contains("\"media-movies\"")
            .contains("\"media-library\"")
            .contains("shares.\"media-admin\"")
            .contains("shares.\"media-downloads\"")
            .contains("shares.\"media-tv\"")
            .contains("shares.\"media-movies\"")
            .contains("shares.\"media-library\"")
            .contains("\"valid users\" = \"media-root\"")
            .contains("\"valid users\" = \"media-root media-downloads\"")
            .contains("\"valid users\" = \"media-root media-tv\"")
            .contains("\"valid users\" = \"media-root media-movies\"")
            .contains("\"valid users\" = \"media-root media-library\"")
            .contains("\"read only\" = \"yes\"")
            .doesNotContain("shares.media =")
            .doesNotContain("\"valid users\" = \"deploy\"")

        assertThat(mediaStorage)
            .contains("/srv/media-views/media-downloads/Downloading")
            .contains("/srv/media-views/media-downloads/Completed")
            .contains("/srv/media-views/media-tv/Completed")
            .contains("/srv/media-views/media-tv/Series")
            .contains("/srv/media-views/media-movies/Completed")
            .contains("/srv/media-views/media-movies/Films")
            .contains("/srv/media-views/media-library/Series")
            .contains("/srv/media-views/media-library/Films")
            .contains("device = \"/srv/media/Downloading\"")
            .contains("device = \"/srv/media/Completed\"")
            .contains("device = \"/srv/media/Series\"")
            .contains("device = \"/srv/media/Films\"")
    }
}
