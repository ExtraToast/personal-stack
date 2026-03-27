package com.jorisjonkers.privatestack.common.vault

interface VaultSecretProvider {
    fun getSecret(
        path: String,
        key: String,
    ): String
}
