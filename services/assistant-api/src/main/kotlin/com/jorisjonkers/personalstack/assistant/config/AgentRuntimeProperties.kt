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
    // Base URL of the knowledge-api MCP server the installed Claude
    // Code hooks read from `KB_URL` (they append `/mcp` themselves).
    // The in-cluster address skips edge + forward-auth, which a CLI
    // can't satisfy.
    val knowledgeBaseUrl: String = "http://knowledge-api.knowledge-system.svc.cluster.local:8080",
    // Secret the hooks' `KB_BEARER_TOKEN` is sourced from; projected
    // from Vault by the agents-kb-bearer VaultStaticSecret.
    val knowledgeBearerSecret: String = "agents-kb-bearer",
    val knowledgeBearerSecretKey: String = "bearer",
    val nodeSelector: Map<String, String> = mapOf("personal-stack/node" to "enschede-gtx-960m-1"),
    val gatewayConnectTimeoutMs: Long = 5_000,
    val gatewayReadTimeoutMs: Long = 60_000,
)
