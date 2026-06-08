package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformSambaNixTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `samba exposes role-scoped media shares with a dedicated all-access identity`() {
        val samba = repositoryRoot.resolve("platform/nix/modules/services/samba.nix").toFile().readText()
        val mediaStorage = repositoryRoot.resolve("platform/nix/modules/services/media-storage.nix").toFile().readText()
        val baseNix = repositoryRoot.resolve("platform/nix/modules/base/default.nix").toFile().readText()
        val globalSambaSettings = samba.substringAfter("global = {").substringBefore("};")

        assertThat(samba)
            .contains("users.users")
            .contains("\"media-root\"")
            .contains("\"media-downloads\"")
            .contains("\"media-series\"")
            .contains("\"media-movies\"")
            .contains("\"media-library\"")
            .contains("\"media-admin\" = adminMediaShare")
            .contains("\"media-downloads\" = writableMediaShare")
            .contains("\"media-series\" = writableMediaShare")
            .contains("\"media-movies\" = writableMediaShare")
            .contains("\"media-library\" = readonlyMediaShare")
            .contains("\"valid users\" = \"media-root\"")
            .contains("\"valid users\" = \"media-root \${roleUser}\"")
            .contains("writableMediaShare \"/srv/media-views/media-downloads\" \"media-downloads\"")
            .contains("writableMediaShare \"/srv/media-views/media-series\" \"media-series\"")
            .contains("writableMediaShare \"/srv/media-views/media-movies\" \"media-movies\"")
            .contains("readonlyMediaShare \"/srv/media-views/media-library\" \"media-library\"")
            .contains("\"read only\" = \"yes\"")
            .contains("timemachine = {")
            .contains("\"vfs objects\" = \"fruit streams_xattr\"")
            .contains("\"fruit:time machine\" = \"yes\"")
            .doesNotContain("shares.")
            .doesNotContain("\"valid users\" = \"deploy\"")
            .doesNotContain("media-tv")
            .doesNotContain("Samba identity for TV")

        assertThat(globalSambaSettings)
            .doesNotContain("fruit")
            .doesNotContain("vfs objects")

        assertThat(mediaStorage)
            .contains("/srv/media-views/media-downloads/Downloading")
            .contains("/srv/media-views/media-downloads/Completed")
            .contains("/srv/media-views/media-series/Completed")
            .contains("/srv/media-views/media-series/Series")
            .contains("/srv/media-views/media-movies/Completed")
            .contains("/srv/media-views/media-movies/Films")
            .contains("/srv/media-views/media-library/Series")
            .contains("/srv/media-views/media-library/Films")
            .contains("device = \"/srv/media/Downloading\"")
            .contains("device = \"/srv/media/Completed\"")
            .contains("device = \"/srv/media/Series\"")
            .contains("device = \"/srv/media/Films\"")
            .contains("systemd.services.personal-stack-media-permissions")
            .contains("unitConfig.RequiresMountsFor = [ \"/srv/media\" ];")
            .contains("\"k3s.service\"")
            .contains("\"/srv/media/Series\"")
            .contains("\"/srv/media/Films\"")
            .contains("\"/var/lib/personal-stack/media/jellyseerr\"")
            .contains("find \${lib.escapeShellArgs mediaApplicationTrees} -xdev")
            .doesNotContain("media-tv")

        assertThat(baseNix)
            .contains("users.groups.deploy.gid = 1000;")
            .contains("users.users.deploy = {")
            .contains("uid = 1000;")
            .contains("group = \"deploy\";")
    }
}
