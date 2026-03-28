package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.application.command.DeleteUserCommand
import com.jorisjonkers.personalstack.auth.application.command.DeleteUserCommandHandler
import com.jorisjonkers.personalstack.auth.application.command.UpdateUserRoleCommand
import com.jorisjonkers.personalstack.auth.application.command.UpdateUserRoleCommandHandler
import com.jorisjonkers.personalstack.auth.application.command.UpdateUserServicePermissionsCommand
import com.jorisjonkers.personalstack.auth.application.command.UpdateUserServicePermissionsCommandHandler
import com.jorisjonkers.personalstack.auth.application.query.GetAllUsersQueryHandler
import com.jorisjonkers.personalstack.auth.application.query.GetUserQueryService
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.AdminUserResponse
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.UpdateRoleRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.UpdateServicePermissionsRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.toServicePermissions
import com.jorisjonkers.personalstack.common.exception.DomainException
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val getAllUsersQueryHandler: GetAllUsersQueryHandler,
    private val getUserQueryService: GetUserQueryService,
    private val updateUserRoleCommandHandler: UpdateUserRoleCommandHandler,
    private val updateUserServicePermissionsCommandHandler: UpdateUserServicePermissionsCommandHandler,
    private val deleteUserCommandHandler: DeleteUserCommandHandler,
) {
    @GetMapping
    fun listUsers(): ResponseEntity<List<AdminUserResponse>> {
        val users = getAllUsersQueryHandler.handle().map { AdminUserResponse.from(it) }
        return ResponseEntity.ok(users)
    }

    @GetMapping("/{id}")
    fun getUser(
        @PathVariable id: UUID,
    ): ResponseEntity<AdminUserResponse> {
        val user = getUserQueryService.findById(UserId(id))
        return ResponseEntity.ok(AdminUserResponse.from(user))
    }

    @PatchMapping("/{id}/role")
    fun updateRole(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRoleRequest,
    ): ResponseEntity<AdminUserResponse> {
        val role = parseRole(request.role)
        val updated = updateUserRoleCommandHandler.handle(UpdateUserRoleCommand(UserId(id), role))
        return ResponseEntity.ok(AdminUserResponse.from(updated))
    }

    @PutMapping("/{id}/services")
    fun updateServicePermissions(
        @PathVariable id: UUID,
        @RequestBody request: UpdateServicePermissionsRequest,
    ): ResponseEntity<AdminUserResponse> {
        val permissions = request.toServicePermissions()
        val updated =
            updateUserServicePermissionsCommandHandler.handle(
                UpdateUserServicePermissionsCommand(UserId(id), permissions),
            )
        return ResponseEntity.ok(AdminUserResponse.from(updated))
    }

    @DeleteMapping("/{id}")
    fun deleteUser(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        deleteUserCommandHandler.handle(DeleteUserCommand(UserId(id)))
        return ResponseEntity.noContent().build()
    }

    private fun parseRole(role: String): Role =
        runCatching { Role.valueOf(role.uppercase()) }.getOrElse {
            throw InvalidRoleException(role)
        }
}

class InvalidRoleException(
    role: String,
) : DomainException("Invalid role: $role", "INVALID_ROLE")
