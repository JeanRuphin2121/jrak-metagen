package io.metagen.meta.controller;

import io.metagen.core.domain.MetaPermission;
import io.metagen.core.domain.MetaRole;
import io.metagen.meta.dto.CreatePermissionRequest;
import io.metagen.meta.dto.CreateRoleRequest;
import io.metagen.meta.service.MetaRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Studio REST API — manage roles and permissions.
 *
 * <h2>Typical RBAC setup workflow</h2>
 * <pre>
 *   1. POST /api/studio/roles                        — create role BUYER
 *   2. POST /api/studio/roles/{id}/permissions        — grant BUYER: READ+WRITE on supplier
 *   3. Assign role to user via /api/studio/users/{id}/roles
 * </pre>
 */
@RestController
@RequestMapping("/api/studio/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Studio API — Roles", description = "Manage roles and RBAC permissions (ADMIN only)")
public class MetaRoleController {

    private final MetaRoleService service;

    // ── Roles ─────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all roles")
    public List<RoleDto> listAll() {
        return service.listRoles().stream().map(RoleDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a role")
    public RoleDto create(@Valid @RequestBody CreateRoleRequest req) {
        return RoleDto.from(service.createRole(req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a role (built-in roles cannot be deleted)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        try {
            service.deleteRole(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    // ── Permissions ───────────────────────────────────────────────────

    @PostMapping("/{id}/permissions")
    @Operation(
        summary = "Grant or update permission for a role on an entity",
        description = "Upsert: creates or updates the permission entry for this (role, entity) pair.")
    public PermissionDto setPermission(
        @PathVariable UUID id,
        @Valid @RequestBody CreatePermissionRequest req
    ) {
        return PermissionDto.from(service.setPermission(id, req));
    }

    @DeleteMapping("/{id}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a permission")
    public ResponseEntity<Void> deletePermission(
        @PathVariable UUID id,
        @PathVariable UUID permissionId
    ) {
        try {
            service.deletePermission(permissionId);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Response DTOs ─────────────────────────────────────────────────

    record RoleDto(UUID id, String name, String label, String description, boolean builtIn) {
        static RoleDto from(MetaRole r) {
            return new RoleDto(r.getId(), r.getName(), r.getLabel(), r.getDescription(), r.isBuiltIn());
        }
    }

    record PermissionDto(UUID id, String entityName, boolean canRead,
                         boolean canWrite, boolean canDelete, String rowFilterScript) {
        static PermissionDto from(MetaPermission p) {
            return new PermissionDto(p.getId(), p.getEntity().getName(),
                                     p.isCanRead(), p.isCanWrite(), p.isCanDelete(),
                                     p.getRowFilterScript());
        }
    }
}
