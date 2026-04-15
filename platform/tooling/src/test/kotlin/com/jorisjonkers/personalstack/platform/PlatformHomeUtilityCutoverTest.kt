package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformHomeUtilityCutoverTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `utility profile imports media storage alongside samba and adguard`() {
        val utilityProfile = repositoryRoot.resolve("platform/nix/profiles/utility.nix").toFile().readText()

        assertThat(utilityProfile)
            .contains("../modules/services/adguard.nix")
            .contains("../modules/services/media-storage.nix")
            .contains("../modules/services/samba.nix")
    }

    @Test
    fun `media storage module prepares legacy media directories and config roots`() {
        val module = repositoryRoot.resolve("platform/nix/modules/services/media-storage.nix").toFile().readText()

        assertThat(module)
            .contains("/srv/media/Completed")
            .contains("/srv/media/Downloading")
            .contains("/srv/media/Films")
            .contains("/srv/media/Series")
            .contains("/srv/media/Anime")
            .contains("/srv/media/TimeMachine")
            .contains("/var/lib/personal-stack/media/qbittorrent")
            .contains("/var/lib/personal-stack/media/prowlarr")
            .contains("/var/lib/personal-stack/media/bazarr")
            .contains("/var/lib/personal-stack/media/sonarr")
            .contains("/var/lib/personal-stack/media/radarr")
            .contains("/var/lib/personal-stack/media/jellyfin")
            .contains("/var/lib/personal-stack/media/jellyseerr")
    }

    @Test
    fun `samba module preserves the legacy share layout on the utility host`() {
        val module = repositoryRoot.resolve("platform/nix/modules/services/samba.nix").toFile().readText()

        assertThat(module)
            .contains("shares.films")
            .contains("shares.series")
            .contains("shares.anime")
            .contains("shares.media")
            .contains("shares.timemachine")
            .contains("\"valid users\" = \"deploy\"")
            .contains("\"fruit:time machine\" = \"yes\"")
            .contains("path = \"/srv/media/TimeMachine\"")
    }

    @Test
    fun `home service cutover playbook documents path mapping and samba bring up`() {
        val playbook =
            repositoryRoot.resolve("platform/cluster/bootstrap/home-service-cutover-playbook.md").toFile().readText()
        val bootstrapReadme = repositoryRoot.resolve("platform/cluster/bootstrap/README.md").toFile().readText()

        assertThat(playbook)
            .contains("/mnt/media")
            .contains("/srv/media")
            .contains("/srv/nomad/qbittorrent")
            .contains("/var/lib/personal-stack/media/qbittorrent")
            .contains("smbpasswd -a deploy")
            .contains("smbclient -L")
            .contains("jellyseerr")
        assertThat(bootstrapReadme).contains("home-service-cutover-playbook.md")
    }
}
