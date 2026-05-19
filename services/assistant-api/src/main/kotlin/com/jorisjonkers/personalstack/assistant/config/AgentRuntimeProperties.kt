package com.jorisjonkers.personalstack.assistant.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Properties that drive Pod creation for workspaces. All sourced from
 * application.yml so the manifests need no rebuild when the image
 * tag or namespace moves.
 */
@ConfigurationProperties(prefix = "agent-runtime")
data class AgentRuntimeProperties(
    val namespace: String,
    val image: String,
    val imagePullPolicy: String = "Always",
    val serviceAccount: String,
    val workspaceStorageClass: String = "local-path",
    val workspaceStorageSize: String = "8Gi",
    val gatewayPort: Int = 8090,
    val claudeCredentialsPvc: String,
    val codexCredentialsPvc: String,
    val githubDeployKeySecret: String,
    val nodeSelector: Map<String, String> = mapOf("personal-stack/node" to "enschede-gtx-960m-1"),
    val gatewayConnectTimeoutMs: Long = 5_000,
    val gatewayReadTimeoutMs: Long = 60_000,
)
