package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class ForwardAuthIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    @Test
    fun `verify endpoint returns 200 with user identity headers for valid session`() {
        val userId = UUID.randomUUID()
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(userId),
                            username = "user-123",
                            roles = listOf("ROLE_USER"),
                        ),
                    ),
                )
            }.andExpect {
                status { isOk() }
                header { string("X-User-Id", userId.toString()) }
                header { string("X-User-Roles", "ROLE_USER") }
            }
    }

    @Test
    fun `verify endpoint redirects to login without authentication`() {
        mockMvc
            .get("/api/v1/auth/verify") {
            }.andExpect {
                status { is3xxRedirection() }
                header { string("Location", startsWith("http://localhost:5174/login?redirect=")) }
            }
    }

    @Test
    fun `verify endpoint preserves original URL from forwarded headers in redirect`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                header("X-Forwarded-Proto", "https")
                header("X-Forwarded-Host", "grafana.jorisjonkers.dev")
                header("X-Forwarded-Uri", "/d/dashboard")
            }.andExpect {
                status { is3xxRedirection() }
                header {
                    string(
                        "Location",
                        "http://localhost:5174/login?redirect=https%3A%2F%2Fgrafana.jorisjonkers.dev%2Fd%2Fdashboard",
                    )
                }
            }
    }

    @Test
    fun `ADMIN user is granted access to any service regardless of explicit permissions`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "admin-1",
                            roles = listOf("ROLE_ADMIN"),
                        ),
                    ),
                )
                header("X-Forwarded-Host", "vault.jorisjonkers.dev")
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `USER with SERVICE_GRAFANA claim is allowed through grafana forward-auth`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "user-2",
                            roles = listOf("ROLE_USER", "SERVICE_GRAFANA"),
                        ),
                    ),
                )
                header("X-Forwarded-Host", "grafana.jorisjonkers.dev")
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `USER with SERVICE_NOMAD claim is allowed through nomad forward-auth`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "user-nomad",
                            roles = listOf("ROLE_USER", "SERVICE_NOMAD"),
                        ),
                    ),
                )
                header("X-Forwarded-Host", "nomad.jorisjonkers.dev")
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `USER without vault permission is denied access to vault`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "user-3",
                            roles = listOf("ROLE_USER", "SERVICE_GRAFANA"),
                        ),
                    ),
                )
                header("X-Forwarded-Host", "vault.jorisjonkers.dev")
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `authenticated request without X-Forwarded-Host returns 200`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "user-4",
                            roles = listOf("ROLE_USER"),
                        ),
                    ),
                )
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `USER with no service permissions is denied access to all protected services`() {
        listOf("vault", "stalwart", "n8n", "grafana", "nomad", "traefik").forEach { subdomain ->
            mockMvc
                .get("/api/v1/auth/verify") {
                    with(
                        user(
                            AuthenticatedUser.of(
                                userId = UserId(UUID.randomUUID()),
                                username = "user-5",
                                roles = listOf("ROLE_USER"),
                            ),
                        ),
                    )
                    header("X-Forwarded-Host", "$subdomain.jorisjonkers.dev")
                }.andExpect {
                    status { isForbidden() }
                }
        }
    }

    @Test
    fun `forward-auth with multiple service permissions works`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "user-multi",
                            roles = listOf("ROLE_USER", "SERVICE_GRAFANA", "SERVICE_VAULT", "SERVICE_N8N"),
                        ),
                    ),
                )
                header("X-Forwarded-Host", "grafana.jorisjonkers.dev")
            }.andExpect {
                status { isOk() }
            }

        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "user-multi",
                            roles = listOf("ROLE_USER", "SERVICE_GRAFANA", "SERVICE_VAULT", "SERVICE_N8N"),
                        ),
                    ),
                )
                header("X-Forwarded-Host", "vault.jorisjonkers.dev")
            }.andExpect {
                status { isOk() }
            }

        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "user-multi",
                            roles = listOf("ROLE_USER", "SERVICE_GRAFANA", "SERVICE_VAULT", "SERVICE_N8N"),
                        ),
                    ),
                )
                header("X-Forwarded-Host", "n8n.jorisjonkers.dev")
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `forward-auth with ADMIN plus SERVICE claims works`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "admin-multi",
                            roles = listOf("ROLE_ADMIN", "SERVICE_GRAFANA"),
                        ),
                    ),
                )
                header("X-Forwarded-Host", "vault.jorisjonkers.dev")
            }.andExpect {
                status { isOk() }
            }

        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "admin-multi",
                            roles = listOf("ROLE_ADMIN", "SERVICE_GRAFANA"),
                        ),
                    ),
                )
                header("X-Forwarded-Host", "grafana.jorisjonkers.dev")
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `forward-auth preserves forwarded headers in redirect`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                header("X-Forwarded-Proto", "https")
                header("X-Forwarded-Host", "n8n.jorisjonkers.dev")
                header("X-Forwarded-Uri", "/workflows/123")
            }.andExpect {
                status { is3xxRedirection() }
                header {
                    string(
                        "Location",
                        "http://localhost:5174/login?redirect=https%3A%2F%2Fn8n.jorisjonkers.dev%2Fworkflows%2F123",
                    )
                }
            }
    }

    @Test
    fun `X-User-Roles header includes all roles from session`() {
        mockMvc
            .get("/api/v1/auth/verify") {
                with(
                    user(
                        AuthenticatedUser.of(
                            userId = UserId(UUID.randomUUID()),
                            username = "user-6",
                            roles = listOf("ROLE_USER", "SERVICE_ASSISTANT"),
                        ),
                    ),
                )
                header("X-Forwarded-Host", "assistant.jorisjonkers.dev")
            }.andExpect {
                status { isOk() }
                header { string("X-User-Roles", "ROLE_USER,SERVICE_ASSISTANT") }
            }
    }
}
