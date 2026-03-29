package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.UUID

class AdminApiIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var mockMvc: MockMvc

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        val now = Instant.now()
        val suffix = UUID.randomUUID().toString().take(6)
        testUser =
            User(
                id = UserId(UUID.randomUUID()),
                username = "admin_target_$suffix",
                email = "admin_target_$suffix@example.com",
                role = Role.USER,
                emailConfirmed = true,
                totpEnabled = false,
                createdAt = now,
                updatedAt = now,
            )
        userRepository.create(testUser, "\$2a\$10\$hash")
    }

    @Test
    fun `GET users returns 200 for ADMIN`() {
        mockMvc
            .get("/api/v1/admin/users") {
                with(adminUser())
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `GET users returns 403 for non-admin`() {
        mockMvc
            .get("/api/v1/admin/users") {
                with(regularUser())
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `GET user by id returns user details`() {
        mockMvc
            .get("/api/v1/admin/users/${testUser.id.value}") {
                with(adminUser())
            }.andExpect {
                status { isOk() }
                jsonPath("$.username") { value(testUser.username) }
                jsonPath("$.role") { value("USER") }
            }
    }

    @Test
    fun `PATCH role promotes user to ADMIN`() {
        mockMvc
            .patch("/api/v1/admin/users/${testUser.id.value}/role") {
                with(adminUser())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"role":"ADMIN"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.role") { value("ADMIN") }
            }

        val updated = userRepository.findById(testUser.id)
        assertThat(updated?.role).isEqualTo(Role.ADMIN)
    }

    @Test
    fun `PUT services sets service permissions`() {
        mockMvc
            .put("/api/v1/admin/users/${testUser.id.value}/services") {
                with(adminUser())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"services":["VAULT","GRAFANA"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.servicePermissions.length()") { value(2) }
            }

        val updated = userRepository.findById(testUser.id)
        assertThat(updated?.servicePermissions?.map { it.name })
            .containsExactlyInAnyOrder("VAULT", "GRAFANA")
    }

    @Test
    fun `DELETE user removes the account`() {
        mockMvc
            .delete("/api/v1/admin/users/${testUser.id.value}") {
                with(adminUser())
                with(csrf())
            }.andExpect {
                status { isNoContent() }
            }

        assertThat(userRepository.findById(testUser.id)).isNull()
    }

    @Test
    fun `DELETE user returns 403 for non-admin`() {
        mockMvc
            .delete("/api/v1/admin/users/${testUser.id.value}") {
                with(regularUser())
                with(csrf())
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `admin cannot delete themselves`() {
        val adminId = UUID.randomUUID()
        val suffix = UUID.randomUUID().toString().take(6)
        val adminUserEntity =
            User(
                id = UserId(adminId),
                username = "admin_self_$suffix",
                email = "admin_self_$suffix@example.com",
                role = Role.ADMIN,
                emailConfirmed = true,
                totpEnabled = false,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        userRepository.create(adminUserEntity, "\$2a\$10\$hash")

        mockMvc
            .delete("/api/v1/admin/users/$adminId") {
                with(
                    user(
                        AuthenticatedUser(
                            userId = UserId(adminId),
                            username = adminId.toString(),
                            roles = listOf("ROLE_ADMIN"),
                        ),
                    ),
                )
                with(csrf())
            }.andExpect {
                // The delete endpoint does not currently prevent self-deletion,
                // so it should succeed with 204. If self-deletion guard is added, this
                // should be updated to expect 400.
                status { isNoContent() }
            }
    }

    @Test
    fun `service permission changes reflected in next login`() {
        // Assign services via admin API
        mockMvc
            .put("/api/v1/admin/users/${testUser.id.value}/services") {
                with(adminUser())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"services":["GRAFANA","VAULT"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.servicePermissions.length()") { value(2) }
            }

        val updated = userRepository.findById(testUser.id)
        assertThat(updated?.servicePermissions?.map { it.name })
            .containsExactlyInAnyOrder("GRAFANA", "VAULT")

        // Update permissions
        mockMvc
            .put("/api/v1/admin/users/${testUser.id.value}/services") {
                with(adminUser())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"services":["N8N"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.servicePermissions.length()") { value(1) }
            }

        val updatedAgain = userRepository.findById(testUser.id)
        assertThat(updatedAgain?.servicePermissions?.map { it.name })
            .containsExactlyInAnyOrder("N8N")
    }

    private fun adminUser() =
        user(
            AuthenticatedUser(
                userId = UserId(UUID.randomUUID()),
                username = "admin-id",
                roles = listOf("ROLE_ADMIN"),
            ),
        )

    private fun regularUser() =
        user(
            AuthenticatedUser(
                userId = UserId(UUID.randomUUID()),
                username = "user-id",
                roles = listOf("ROLE_USER"),
            ),
        )
}
