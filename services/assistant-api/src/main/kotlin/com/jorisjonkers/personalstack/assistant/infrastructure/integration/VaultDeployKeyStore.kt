package com.jorisjonkers.personalstack.assistant.infrastructure.integration

import com.jorisjonkers.personalstack.assistant.domain.model.GithubLinkId
import com.jorisjonkers.personalstack.assistant.domain.model.ProjectId
import com.jorisjonkers.personalstack.assistant.domain.port.DeployKeyStore
import com.jorisjonkers.personalstack.common.vault.VaultKeyValueWriter
import com.jorisjonkers.personalstack.common.vault.VaultSecretProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.Base64

/**
 * Vault-backed adapter that materialises a deploy key under the
 * project-scoped path. Format matches the existing
 * `agents-github-deploy-key` Secret consumed by the runner image:
 * three fields — `private_key`, `public_key`, `known_hosts` —
 * plus a derived `fingerprint` written alongside so a Vault dump
 * is enough to verify what key is bound where.
 */
@Component
@ConditionalOnProperty("spring.cloud.vault.enabled", havingValue = "true", matchIfMissing = false)
open class VaultDeployKeyStore(
    private val writer: VaultKeyValueWriter,
    private val reader: VaultSecretProvider,
) : DeployKeyStore {
    override fun store(
        projectId: ProjectId,
        linkId: GithubLinkId,
        privateKeyOpenssh: String,
        publicKeyOpenssh: String,
        knownHosts: String,
    ): DeployKeyStore.StoredKey {
        val fingerprint = sha256Fingerprint(publicKeyOpenssh)
        val path = vaultPath(projectId, linkId)
        writer.writeSecret(
            path,
            mapOf(
                "private_key" to privateKeyOpenssh.trim() + "\n",
                "public_key" to publicKeyOpenssh.trim() + "\n",
                "known_hosts" to knownHosts.trim() + "\n",
                "fingerprint" to fingerprint,
            ),
        )
        return DeployKeyStore.StoredKey(fingerprint = fingerprint, vaultPath = path)
    }

    override fun remove(
        projectId: ProjectId,
        linkId: GithubLinkId,
    ) {
        writer.deleteSecret(vaultPath(projectId, linkId))
    }

    override fun readPublicKey(
        projectId: ProjectId,
        linkId: GithubLinkId,
    ): String? = runCatching { reader.getSecret(vaultPath(projectId, linkId), "public_key") }.getOrNull()

    override fun loadKey(
        projectId: ProjectId,
        linkId: GithubLinkId,
    ): DeployKeyStore.KeyMaterial? {
        val path = vaultPath(projectId, linkId)
        val priv = runCatching { reader.getSecret(path, "private_key") }.getOrNull() ?: return null
        val pub = runCatching { reader.getSecret(path, "public_key") }.getOrNull() ?: return null
        val known = runCatching { reader.getSecret(path, "known_hosts") }.getOrNull() ?: ""
        val fp = runCatching { reader.getSecret(path, "fingerprint") }.getOrNull() ?: ""
        return DeployKeyStore.KeyMaterial(privateKey = priv, publicKey = pub, knownHosts = known, fingerprint = fp)
    }

    private fun vaultPath(
        projectId: ProjectId,
        linkId: GithubLinkId,
    ): String = "secret/data/agents/projects/$projectId/repos/$linkId"

    private fun sha256Fingerprint(publicKey: String): String {
        // GitHub-compatible OpenSSH SHA-256 fingerprint: the
        // base64-encoded sha256 of the raw key body (the middle
        // field between "ssh-ed25519" and the comment), with the
        // trailing `=` padding stripped.
        val parts = publicKey.trim().split(Regex("\\s+"))
        if (parts.size < 2) error("malformed public key — expected `<algo> <base64> [comment]`")
        val body = Base64.getDecoder().decode(parts[1])
        val digest = MessageDigest.getInstance("SHA-256").digest(body)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }
}
