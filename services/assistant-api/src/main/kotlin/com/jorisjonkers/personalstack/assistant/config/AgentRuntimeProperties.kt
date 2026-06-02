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
    // Base URL of the standing agent-gateway used for the
    // workspace-independent `/git/verify` deploy-key probe at
    // attach/create time (the per-workspace gateway sidecar only
    // exists once a runner Pod is up). Empty => verify is skipped and
    // the stored result degrades to read=write=null.
    val verifyGatewayBaseUrl: String = "",
    // Read-only GitHub API token for the branch-protection check.
    // Empty => branch protection reports null (never a hard failure).
    val githubApiToken: String = "",
    // GitHub REST base — overridable for tests / GitHub Enterprise.
    val githubApiBaseUrl: String = "https://api.github.com",
    // GitHub App credentials used to mint short-lived, repo-scoped
    // installation tokens for the runner's `gh` (PR comments + Actions
    // re-runs only — pull_requests:write + actions:write, never
    // contents/administration). The App's numeric id and its PEM
    // private key (PKCS#1 or PKCS#8). Both empty => minting disabled
    // and the internal token endpoint reports 503.
    val githubAppId: String = "",
    val githubAppPrivateKey: String = "",
    // Shared bearer the runner must present on the in-cluster
    // /api/v1/internal/github/installation-token call. Empty => the
    // internal endpoint is fail-closed (rejects every request) so an
    // unconfigured deployment never exposes token minting unauthed.
    val githubAppTokenBearer: String = "",
)
