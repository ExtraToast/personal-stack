package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jorisjonkers.personalstack.auth.application.command.DeleteUserCommandHandler
import com.jorisjonkers.personalstack.auth.application.command.UpdateUserRoleCommandHandler
import com.jorisjonkers.personalstack.auth.application.command.UpdateUserServicePermissionsCommandHandler
import com.jorisjonkers.personalstack.auth.application.query.GetAllUsersQueryHandler
import com.jorisjonkers.personalstack.auth.application.query.GetUserQueryService
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.UpdateRoleRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.UpdateServicePermissionsRequest
import com.jorisjonkers.personalstack.common.exception.NotFoundException
import com.jorisjonkers.personalstack.common.web.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class AdminControllerTest {
    private val getAllUsersQueryHandler = mockk<GetAllUsersQueryHandler>()
    private val getUserQueryService = mockk<GetUserQueryService>()
    private val updateUserRoleCommandHandler = mockk<UpdateUserRoleCommandHandler>()
    private val updateUserServicePermissionsCommandHandler = mockk<UpdateUserServicePermissionsCommandHandler>()
    private val deleteUserCommandHandler = mockk<DeleteUserCommandHandler>()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private lateinit var mockMvc: MockMvc

    private val userId = UserId(UUID.randomUUID())
    private val now = Instant.now()
    private val user =
        User(
            id = userId,
            username = "alice",
            email = "alice@example.com",
            role = Role.USER,
            emailConfirmed = true,
            totpEnabled = false,
            createdAt = now,
            updatedAt = now,
            servicePermissions = setOf(ServicePermission.GRAFANA),
        )

    @BeforeEach
    fun setUp() {
        val controller =
            AdminController(
                getAllUsersQueryHandler,
                getUserQueryService,
                updateUserRoleCommandHandler,
                updateUserServicePermissionsCommandHandler,
                deleteUserCommandHandler,
            )
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `GET users returns list of users`() {
        every { getAllUsersQueryHandler.handle() } returns listOf(user)

        mockMvc
            .get("/api/v1/admin/users")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].username") { value("alice") }
                jsonPath("$[0].role") { value("USER") }
                jsonPath("$[0].servicePermissions[0]") { value("GRAFANA") }
            }
    }

    @Test
    fun `GET users by id returns user`() {
        every { getUserQueryService.findById(userId) } returns user

        mockMvc
            .get("/api/v1/admin/users/${userId.value}")
            .andExpect {
                status { isOk() }
                jsonPath("$.username") { value("alice") }
            }
    }

    @Test
    fun `GET users by id returns 404 when not found`() {
        every { getUserQueryService.findById(userId) } throws NotFoundException("User", userId.value.toString())

        mockMvc
            .get("/api/v1/admin/users/${userId.value}")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `PATCH role updates user role`() {
        val adminUser = user.copy(role = Role.ADMIN)
        every { updateUserRoleCommandHandler.handle(any()) } returns adminUser

        mockMvc
            .patch("/api/v1/admin/users/${userId.value}/role") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateRoleRequest("ADMIN"))
            }.andExpect {
                status { isOk() }
                jsonPath("$.role") { value("ADMIN") }
            }
    }

    @Test
    fun `PATCH role returns 400 for invalid role value`() {
        mockMvc
            .patch("/api/v1/admin/users/${userId.value}/role") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateRoleRequest("SUPERUSER"))
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `PUT services updates service permissions`() {
        val updated = user.copy(servicePermissions = setOf(ServicePermission.VAULT, ServicePermission.N8N))
        every { updateUserServicePermissionsCommandHandler.handle(any()) } returns updated

        mockMvc
            .put("/api/v1/admin/users/${userId.value}/services") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateServicePermissionsRequest(listOf("VAULT", "N8N")))
            }.andExpect {
                status { isOk() }
                jsonPath("$.servicePermissions.length()") { value(2) }
            }
    }

    @Test
    fun `DELETE user returns 204`() {
        every { deleteUserCommandHandler.handle(any()) } returns Unit

        mockMvc
            .delete("/api/v1/admin/users/${userId.value}")
            .andExpect {
                status { isNoContent() }
            }

        verify { deleteUserCommandHandler.handle(any()) }
    }

    @Test
    fun `DELETE user returns 404 when not found`() {
        every { deleteUserCommandHandler.handle(any()) } throws NotFoundException("User", userId.value.toString())

        mockMvc
            .delete("/api/v1/admin/users/${userId.value}")
            .andExpect {
                status { isNotFound() }
            }
    }
}
