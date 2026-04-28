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
            .contains("\"media-admin\" = adminMediaShare")
            .contains("\"media-downloads\" = writableMediaShare")
            .contains("\"media-tv\" = writableMediaShare")
            .contains("\"media-movies\" = writableMediaShare")
            .contains("\"media-library\" = readonlyMediaShare")
            .contains("\"valid users\" = \"media-root\"")
            .contains("\"valid users\" = \"media-root \${roleUser}\"")
            .contains("writableMediaShare \"/srv/media-views/media-downloads\" \"media-downloads\"")
            .contains("writableMediaShare \"/srv/media-views/media-tv\" \"media-tv\"")
            .contains("writableMediaShare \"/srv/media-views/media-movies\" \"media-movies\"")
            .contains("readonlyMediaShare \"/srv/media-views/media-library\" \"media-library\"")
            .contains("\"read only\" = \"yes\"")
            .doesNotContain("shares.")
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
