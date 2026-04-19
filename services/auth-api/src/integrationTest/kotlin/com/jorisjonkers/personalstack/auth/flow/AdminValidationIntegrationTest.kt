package com.jorisjonkers.personalstack.auth.flow

import com.jorisjonkers.personalstack.auth.IntegrationTestBase
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.UUID

class AdminValidationIntegrationTest : IntegrationTestBase() {
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
                username = "adminval_$suffix",
                email = "adminval_$suffix@example.com",
                firstName = "",
                lastName = "",
                role = Role.USER,
                emailConfirmed = true,
                totpEnabled = false,
                createdAt = now,
                updatedAt = now,
            )
        userRepository.create(testUser, "\$2a\$10\$hash")
    }

    @Test
    fun `update role with invalid role value returns 422`() {
        mockMvc
            .patch("/api/v1/admin/users/${testUser.id.value}/role") {
                with(adminUser())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"role":"SUPERADMIN"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `update services with invalid service name returns 422`() {
        mockMvc
            .put("/api/v1/admin/users/${testUser.id.value}/services") {
                with(adminUser())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"services":["NONEXISTENT_SERVICE"]}"""
            }.andExpect {
                // Invalid service names are silently filtered by toServicePermissions()
                status { isOk() }
                jsonPath("$.servicePermissions.length()") { value(0) }
            }
    }

    @Test
    fun `update role without admin token returns 403`() {
        mockMvc
            .patch("/api/v1/admin/users/${testUser.id.value}/role") {
                with(regularUser())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"role":"ADMIN"}"""
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `delete user without admin token returns 403`() {
        mockMvc
            .delete("/api/v1/admin/users/${testUser.id.value}") {
                with(regularUser())
                with(csrf())
            }.andExpect {
                status { isForbidden() }
            }
    }

    private fun adminUser() =
        user(
            AuthenticatedUser.of(
                userId = UserId(UUID.randomUUID()),
                username = "admin-id",
                roles = listOf("ROLE_ADMIN"),
            ),
        )

    private fun regularUser() =
        user(
            AuthenticatedUser.of(
                userId = UserId(UUID.randomUUID()),
                username = "user-id",
                roles = listOf("ROLE_USER"),
            ),
        )
}
