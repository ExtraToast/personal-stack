package com.jorisjonkers.privatestack.common.vault

import org.springframework.stereotype.Component
import org.springframework.vault.core.VaultTemplate

/**
 * HashiCorp Vault implementation of [VaultSecretProvider].
 * Fetches secrets from Vault's KV v2 secret engine at runtime.
 *
 * Configure via spring.cloud.vault.* properties or application.yml.
 */
@Component
class SpringVaultSecretProvider(
    private val vaultTemplate: VaultTemplate,
) : VaultSecretProvider {

    override fun getSecret(path: String, key: String): String {
        val response = vaultTemplate.read(path)
            ?: error("No secret found at Vault path: $path")
        return response.data?.get(key)?.toString()
            ?: error("Key '$key' not found at Vault path: $path")
    }
}
