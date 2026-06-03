package com.jorisjonkers.personalstack.assistant.infrastructure.integration

import com.fasterxml.jackson.annotation.JsonProperty
import com.jorisjonkers.personalstack.assistant.config.AgentRuntimeProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

/**
 * Mints short-lived, single-repo GitHub App installation tokens so the
 * runner's `gh` can create PRs/re-run Actions and `git push` can create
 * feature branches over HTTPS. Each token is scoped to the one
 * repository being acted on and carries only `contents:write`,
 * `pull_requests:write`, and `actions:write`; `administration` is never
 * requested, so the token cannot change repo settings. `main` stays
 * guarded by branch protection.
 *
 * Disabled — [enabled] is false and [mint] returns null — whenever the
 * App id or private key is absent, so an unconfigured deployment is a
 * no-op rather than a failure.
 */
@Component
class GitHubAppInstallationTokenClient(
    private val restClient: RestClient,
    private val props: AgentRuntimeProperties,
) {
    private val log = LoggerFactory.getLogger(GitHubAppInstallationTokenClient::class.java)

    val enabled: Boolean
        get() = props.githubAppId.isNotBlank() && props.githubAppPrivateKey.isNotBlank()

    data class InstallationToken(
        val token: String,
        val expiresAt: Instant,
    )

    /**
     * Returns a fresh installation token scoped to [repoUrl]'s repo, or
     * null when minting is disabled, the URL is unparseable, the App is
     * not installed on that owner, or any transport/GitHub error occurs.
     * Never throws — the caller maps null to 503.
     */
    fun mint(repoUrl: String): InstallationToken? {
        if (!enabled) return null
        val slug = GitHubBranchProtectionClient.parseOwnerRepo(repoUrl)
        if (slug == null) {
            log.warn("installation-token mint skipped — could not parse owner/repo from {}", repoUrl)
            return null
        }
        val base = props.githubApiBaseUrl.trim().trimEnd('/')
        return runCatching {
            val jwt = appJwt()
            val installationId =
                installationId(base, slug, jwt)
                    ?: error("no installation for ${slug.owner}/${slug.repo}")
            val resp =
                accessToken(base, installationId, slug.repo, jwt)
                    ?: error("empty access-token response")
            InstallationToken(token = resp.token, expiresAt = Instant.parse(resp.expiresAt))
        }.onFailure {
            log.warn("installation-token mint for {} failed: {}", repoUrl, it.message)
        }.getOrNull()
    }

    private fun installationId(
        base: String,
        slug: GitHubBranchProtectionClient.OwnerRepo,
        jwt: String,
    ): Long? =
        restClient
            .get()
            .uri("$base/repos/${slug.owner}/${slug.repo}/installation")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
            .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .header(GH_API_VERSION_HEADER, GH_API_VERSION)
            .retrieve()
            .body(InstallationResponse::class.java)
            ?.id

    private fun accessToken(
        base: String,
        installationId: Long,
        repo: String,
        jwt: String,
    ): AccessTokenResponse? =
        restClient
            .post()
            .uri("$base/app/installations/$installationId/access_tokens")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
            .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .header(GH_API_VERSION_HEADER, GH_API_VERSION)
            .body(
                AccessTokenRequest(
                    repositories = listOf(repo),
                    permissions =
                        mapOf(
                            "contents" to "write",
                            "pull_requests" to "write",
                            "actions" to "write",
                        ),
                ),
            ).retrieve()
            .body(AccessTokenResponse::class.java)

    /**
     * RS256 JWT asserting the App's identity, valid for ~9 minutes
     * (GitHub caps App JWTs at 10). `iat` is backdated 60s to absorb
     * clock skew between this Pod and GitHub.
     */
    private fun appJwt(): String {
        val now = Instant.now().epochSecond
        val header = b64Url("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val payload = b64Url("""{"iat":${now - 60},"exp":${now + 540},"iss":"${props.githubAppId}"}""".toByteArray())
        val signingInput = "$header.$payload"
        val signature = b64Url(sign(signingInput.toByteArray()))
        return "$signingInput.$signature"
    }

    private fun sign(input: ByteArray): ByteArray {
        val spec = PKCS8EncodedKeySpec(pkcs8Der(props.githubAppPrivateKey))
        val key = KeyFactory.getInstance("RSA").generatePrivate(spec)
        return Signature.getInstance("SHA256withRSA").run {
            initSign(key)
            update(input)
            sign()
        }
    }

    private data class AccessTokenRequest(
        val repositories: List<String>,
        val permissions: Map<String, String>,
    )

    private data class InstallationResponse(
        val id: Long = 0,
    )

    private data class AccessTokenResponse(
        val token: String = "",
        @param:JsonProperty("expires_at") val expiresAt: String = "",
    )

    // The DER/ASN.1 byte and bit-shift literals below are structural
    // crypto constants, not tunable values.
    @Suppress("MagicNumber")
    companion object {
        private const val GH_API_VERSION_HEADER = "X-GitHub-Api-Version"
        private const val GH_API_VERSION = "2022-11-28"

        // The fixed PKCS#8 PrivateKeyInfo prelude for an RSA key:
        // version INTEGER 0 + AlgorithmIdentifier { rsaEncryption, NULL }.
        private val RSA_PKCS8_ALG_ID =
            byteArrayOf(0x02, 0x01, 0x00) +
                byteArrayOf(
                    0x30,
                    0x0d,
                    0x06,
                    0x09,
                    0x2a,
                    0x86.toByte(),
                    0x48,
                    0x86.toByte(),
                    0xf7.toByte(),
                    0x0d,
                    0x01,
                    0x01,
                    0x01,
                    0x05,
                    0x00,
                )

        private fun b64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        /**
         * Returns PKCS#8 DER for a PEM private key. A PKCS#8 PEM
         * (`BEGIN PRIVATE KEY`) is decoded directly; a PKCS#1 PEM
         * (`BEGIN RSA PRIVATE KEY`, the format GitHub hands out) is
         * wrapped in the PKCS#8 envelope so `KeyFactory` accepts it
         * without a BouncyCastle dependency.
         */
        fun pkcs8Der(pem: String): ByteArray {
            val der = Base64.getMimeDecoder().decode(stripPem(pem))
            return if (pem.contains("BEGIN RSA PRIVATE KEY")) wrapPkcs1AsPkcs8(der) else der
        }

        private fun stripPem(pem: String): String =
            pem
                .replace(Regex("-----BEGIN [^-]+-----"), "")
                .replace(Regex("-----END [^-]+-----"), "")
                .replace(Regex("\\s"), "")

        private fun wrapPkcs1AsPkcs8(pkcs1: ByteArray): ByteArray {
            val privateKeyOctet = derTlv(0x04, pkcs1)
            return derTlv(0x30, RSA_PKCS8_ALG_ID + privateKeyOctet)
        }

        // Emits a DER tag-length-value with definite long-form length.
        private fun derTlv(
            tag: Int,
            content: ByteArray,
        ): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(tag)
            val len = content.size
            if (len < 0x80) {
                out.write(len)
            } else {
                val lenBytes =
                    generateSequence(len) { if (it > 0) it shr 8 else null }
                        .takeWhile { it > 0 }
                        .map { (it and 0xff).toByte() }
                        .toList()
                        .reversed()
                out.write(0x80 or lenBytes.size)
                lenBytes.forEach { out.write(it.toInt()) }
            }
            out.write(content)
            return out.toByteArray()
        }
    }
}
