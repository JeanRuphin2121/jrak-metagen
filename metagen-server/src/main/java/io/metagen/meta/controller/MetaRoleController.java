package io.metagen.meta.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.metagen.core.domain.MetaPermission;
import io.metagen.core.domain.MetaRole;
import io.metagen.engine.config.MetaYamlCodec;
import io.metagen.meta.dto.CreatePermissionRequest;
import io.metagen.meta.dto.CreateRoleRequest;
import io.metagen.meta.service.MetaRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/studio/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Studio API — Roles", description = "Manage roles and RBAC permissions (ADMIN only)")
public class MetaRoleController {

    private final MetaRoleService service;

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
    public void delete(@PathVariable UUID id) {
        service.deleteRole(id);
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "List a role's permissions")
    public List<PermissionDto> permissions(@PathVariable UUID id) {
        return service.listPermissions(id).stream().map(PermissionDto::from).toList();
    }

    @PostMapping("/{id}/permissions")
    @Operation(
        summary = "Grant or update a permission for a role on an entity",
        description = "Upsert on the (role, entity) pair. `rowFilter` is a declarative condition, "
                    + "e.g. {\"field\":\"created_by\",\"op\":\"eq\",\"value\":\"$user\"}.")
    public PermissionDto setPermission(@PathVariable UUID id, @Valid @RequestBody CreatePermissionRequest req) {
        return PermissionDto.from(service.setPermission(id, req));
    }

    @DeleteMapping("/{id}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a permission")
    public void deletePermission(@PathVariable UUID id, @PathVariable UUID permissionId) {
        service.deletePermission(id, permissionId);
    }

    record RoleDto(UUID id, String name, String label, String description, boolean builtIn) {
        static RoleDto from(MetaRole r) {
            return new RoleDto(r.getId(), r.getName(), r.getLabel(), r.getDescription(), r.isBuiltIn());
        }
    }

    record PermissionDto(UUID id, String entityName, boolean canRead, boolean canWrite, boolean canDelete,
                         JsonNode rowFilter, JsonNode fieldMask) {
        static PermissionDto from(MetaPermission p) {
            return new PermissionDto(p.getId(), p.getEntity().getName(),
                p.isCanRead(), p.isCanWrite(), p.isCanDelete(),
                MetaYamlCodec.parseJson(p.getRowFilter()), p.getFieldMask());
        }
    }
}
