package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.Repository
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.port.DeployKeyStore
import com.jorisjonkers.personalstack.assistant.domain.port.RepositoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class AttachRepositoryDeployKeyCommandHandlerTest {
    private val repositories = mockk<RepositoryRepository>()
    private val deployKeys = mockk<DeployKeyStore>()
    private val deployKeysProvider =
        mockk<org.springframework.beans.factory.ObjectProvider<DeployKeyStore>> {
            every { ifAvailable } returns deployKeys
        }
    private val handler = AttachRepositoryDeployKeyCommandHandler(repositories, deployKeysProvider)

    private val sampleRepo =
        Repository(
            id = RepositoryId.random(),
            name = "personal-stack",
            repoUrl = "git@github.com:ExtraToast/personal-stack.git",
            defaultBranch = "main",
            vaultKeyPath = "secret/data/agents/repositories/x",
            deployKeyFingerprint = null,
            deployKeyAddedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private val validPrivate =
        """
        -----BEGIN OPENSSH PRIVATE KEY-----
        deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef
        -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()
    private val validPublic =
        "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl test@laptop"

    @Test
    fun `handle stores the key in Vault and mirrors the fingerprint`() {
        every { repositories.findById(sampleRepo.id) } returns sampleRepo
        every {
            deployKeys.store(any<RepositoryId>(), any(), any(), any())
        } returns
            DeployKeyStore.StoredKey(
                fingerprint = "SHA256:abcdef",
                vaultPath = sampleRepo.vaultKeyPath,
            )
        val saved = slot<Repository>()
        every { repositories.save(capture(saved)) } answers { saved.captured }

        handler.handle(
            AttachRepositoryDeployKeyCommand(
                repositoryId = sampleRepo.id,
                privateKeyOpenssh = validPrivate,
                publicKeyOpenssh = validPublic,
                knownHosts = null,
            ),
        )

        assertThat(saved.captured.deployKeyFingerprint).isEqualTo("SHA256:abcdef")
        assertThat(saved.captured.deployKeyAddedAt).isNotNull
        verify { deployKeys.store(sampleRepo.id, validPrivate, validPublic, any()) }
    }

    @Test
    fun `handle uses static known_hosts fallback when none supplied`() {
        every { repositories.findById(sampleRepo.id) } returns sampleRepo
        val fallback = slot<String>()
        every {
            deployKeys.store(any<RepositoryId>(), any(), any(), capture(fallback))
        } returns DeployKeyStore.StoredKey("SHA256:x", sampleRepo.vaultKeyPath)
        every { repositories.save(any()) } answers { firstArg() }

        handler.handle(
            AttachRepositoryDeployKeyCommand(
                repositoryId = sampleRepo.id,
                privateKeyOpenssh = validPrivate,
                publicKeyOpenssh = validPublic,
                knownHosts = "  ",
            ),
        )

        assertThat(fallback.captured).contains("github.com ssh-ed25519")
    }

    @Test
    fun `handle rejects malformed private key`() {
        every { repositories.findById(sampleRepo.id) } returns sampleRepo
        assertThrows<IllegalArgumentException> {
            handler.handle(
                AttachRepositoryDeployKeyCommand(
                    repositoryId = sampleRepo.id,
                    privateKeyOpenssh = "not a key",
                    publicKeyOpenssh = validPublic,
                ),
            )
        }
    }

    @Test
    fun `handle errors on unknown repository`() {
        every { repositories.findById(any()) } returns null
        assertThrows<IllegalStateException> {
            handler.handle(
                AttachRepositoryDeployKeyCommand(
                    repositoryId = RepositoryId.random(),
                    privateKeyOpenssh = validPrivate,
                    publicKeyOpenssh = validPublic,
                ),
            )
        }
    }
}
