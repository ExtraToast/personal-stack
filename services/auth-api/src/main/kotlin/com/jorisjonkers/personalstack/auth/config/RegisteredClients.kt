package com.jorisjonkers.personalstack.auth.config

import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.time.Duration
import java.util.UUID

private val ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(15)
private val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(7)

private fun deterministicId(clientId: String): String = UUID.nameUUIDFromBytes(clientId.toByteArray()).toString()

private fun defaultTokenSettings(): TokenSettings =
    TokenSettings
        .builder()
        .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
        .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
        .reuseRefreshTokens(false)
        .build()

private fun noConsentSettings(requirePkce: Boolean): ClientSettings =
    ClientSettings
        .builder()
        .requireProofKey(requirePkce)
        .requireAuthorizationConsent(false)
        .build()

fun buildAuthUiClient(): RegisteredClient =
    RegisteredClient
        .withId(deterministicId("auth-ui"))
        .clientId("auth-ui")
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("https://auth.jorisjonkers.dev/callback")
        .redirectUri("https://auth.jorisjonkers.test/callback")
        .redirectUri("http://localhost:5174/callback")
        .postLogoutRedirectUri("https://auth.jorisjonkers.dev/logged-out")
        .postLogoutRedirectUri("https://auth.jorisjonkers.test/logged-out")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope(OidcScopes.EMAIL)
        .clientSettings(noConsentSettings(requirePkce = true))
        .tokenSettings(defaultTokenSettings())
        .build()

fun buildAppUiClient(): RegisteredClient =
    RegisteredClient
        .withId(deterministicId("app-ui"))
        .clientId("app-ui")
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("https://jorisjonkers.dev/callback")
        .redirectUri("https://jorisjonkers.test/callback")
        .redirectUri("http://localhost:5175/callback")
        .postLogoutRedirectUri("https://jorisjonkers.dev")
        .postLogoutRedirectUri("https://jorisjonkers.test")
        .postLogoutRedirectUri("http://localhost:5175")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope(OidcScopes.EMAIL)
        .clientSettings(noConsentSettings(requirePkce = true))
        .tokenSettings(defaultTokenSettings())
        .build()

fun buildAssistantApiClient(): RegisteredClient =
    RegisteredClient
        .withId(deterministicId("assistant-api"))
        .clientId("assistant-api")
        .clientSecret("{noop}assistant-secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .scope("api.read")
        .scope("api.write")
        .tokenSettings(
            TokenSettings
                .builder()
                .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                .build(),
        ).build()

fun buildGrafanaClient(secret: String): RegisteredClient =
    RegisteredClient
        .withId(deterministicId("grafana"))
        .clientId("grafana")
        .clientSecret("{noop}$secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("https://grafana.jorisjonkers.dev/login/generic_oauth")
        .redirectUri("https://grafana.jorisjonkers.test/login/generic_oauth")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope(OidcScopes.EMAIL)
        .clientSettings(noConsentSettings(requirePkce = false))
        .tokenSettings(defaultTokenSettings())
        .build()

fun buildN8nClient(secret: String): RegisteredClient =
    RegisteredClient
        .withId(deterministicId("n8n"))
        .clientId("n8n")
        .clientSecret("{noop}$secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("https://n8n.jorisjonkers.dev/auth/oidc/callback")
        .redirectUri("https://n8n.jorisjonkers.test/auth/oidc/callback")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope(OidcScopes.EMAIL)
        .clientSettings(noConsentSettings(requirePkce = false))
        .tokenSettings(defaultTokenSettings())
        .build()

fun buildRabbitMqClient(): RegisteredClient =
    RegisteredClient
        .withId(deterministicId("rabbitmq"))
        .clientId("rabbitmq")
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("https://rabbitmq.jorisjonkers.dev/js/oidc-oauth/login-callback.html")
        .redirectUri("https://rabbitmq.jorisjonkers.test/js/oidc-oauth/login-callback.html")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope(OidcScopes.EMAIL)
        .clientSettings(noConsentSettings(requirePkce = true))
        .tokenSettings(defaultTokenSettings())
        .build()

fun buildHeadlampClient(): RegisteredClient =
    RegisteredClient
        .withId(deterministicId("headlamp"))
        .clientId("headlamp")
        // Public client with PKCE — same pattern as rabbitmq. There is no
        // client secret to manage anywhere (no Vault key, no K8s secret),
        // which removes the one-time-bootstrap step from the OIDC flow.
        // The Headlamp backend proves possession of the auth code via
        // the PKCE verifier instead.
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("https://dashboard.jorisjonkers.dev/oidc-callback")
        .redirectUri("https://dashboard.jorisjonkers.test/oidc-callback")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope(OidcScopes.EMAIL)
        // `groups` is a non-standard scope the token customizer recognises
        // and populates with the Kubernetes group membership the k3s API
        // server reads via --oidc-groups-claim. The chain is: DASHBOARD
        // ServicePermission -> `k8s-admin` in the groups claim ->
        // cluster-admin via the oidc:k8s-admin ClusterRoleBinding.
        .scope("groups")
        .clientSettings(noConsentSettings(requirePkce = true))
        .tokenSettings(defaultTokenSettings())
        .build()

fun buildVaultClient(secret: String): RegisteredClient =
    RegisteredClient
        .withId(deterministicId("vault"))
        .clientId("vault")
        .clientSecret("{noop}$secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("https://vault.jorisjonkers.dev/ui/vault/auth/oidc/oidc/callback")
        .redirectUri("https://vault.jorisjonkers.test/ui/vault/auth/oidc/oidc/callback")
        .redirectUri("http://localhost:8250/oidc/callback")
        .redirectUri("http://127.0.0.1:8250/oidc/callback")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope(OidcScopes.EMAIL)
        .clientSettings(noConsentSettings(requirePkce = false))
        .tokenSettings(defaultTokenSettings())
        .build()
