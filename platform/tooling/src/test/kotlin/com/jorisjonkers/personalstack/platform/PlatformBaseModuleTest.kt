package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformBaseModuleTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `base module hardens ssh to key only access on port 2222`() {
        val module = repositoryRoot.resolve("platform/nix/modules/base/default.nix").toFile().readText()

        assertThat(module)
            .contains("networking.firewall.allowedTCPPorts = [ 2222 ]")
            .contains("ports = [ 2222 ]")
            .contains("AllowUsers = [ \"deploy\" ]")
            .contains("KbdInteractiveAuthentication = false")
            .contains("PasswordAuthentication = false")
            .contains("PermitRootLogin = \"no\"")
            .contains("PubkeyAuthentication = true")
            .contains("authorizedKeysDir = ../../authorized-keys")
            .contains("hostAuthorizedKeyPath = authorizedKeysDir + \"/\${config.networking.hostName}.pub\"")
            .contains("openssh.authorizedKeys.keys = lib.optional (hostAuthorizedKey != null) hostAuthorizedKey")
            .doesNotContain("users.users.root.openssh.authorizedKeys.keys")
    }
}
