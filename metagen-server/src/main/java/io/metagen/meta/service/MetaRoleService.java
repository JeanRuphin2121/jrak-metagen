package io.metagen.meta.service;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaPermission;
import io.metagen.core.domain.MetaRole;
import io.metagen.core.repository.MetaEntityRepository;
import io.metagen.core.repository.MetaPermissionRepository;
import io.metagen.core.repository.MetaRoleRepository;
import io.metagen.meta.dto.CreatePermissionRequest;
import io.metagen.meta.dto.CreateRoleRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Business logic for roles and permissions in the Studio. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaRoleService {

    private final MetaRoleRepository       roleRepository;
    private final MetaEntityRepository     entityRepository;
    private final MetaPermissionRepository permissionRepository;

    // ── Roles ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MetaRole> listRoles() {
        return roleRepository.findAll();
    }

    @Transactional
    public MetaRole createRole(CreateRoleRequest req) {
        if (roleRepository.existsByName(req.name())) {
            throw new IllegalArgumentException("Role '" + req.name() + "' already exists");
        }
        MetaRole role = MetaRole.builder()
            .name(req.name())
            .label(req.label())
            .description(req.description())
            .builtIn(false)
            .build();
        MetaRole saved = roleRepository.save(role);
        log.info("Created role '{}'", saved.getName());
        return saved;
    }

    @Transactional
    public void deleteRole(UUID id) {
        MetaRole role = roleRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Role not found: " + id));
        if (role.isBuiltIn()) {
            throw new IllegalArgumentException("Cannot delete built-in role '" + role.getName() + "'");
        }
        roleRepository.delete(role);
        log.info("Deleted role '{}'", role.getName());
    }

    // ── Permissions ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MetaPermission> listPermissions(UUID roleId) {
        return permissionRepository.findByEntityId(roleId); // reuse by role in impl
    }

    /**
     * Grants (or updates) permission for a role on an entity.
     * If a permission already exists for this (role, entity) pair it is updated.
     */
    @Transactional
    public MetaPermission setPermission(UUID roleId, CreatePermissionRequest req) {
        MetaRole   role   = roleRepository.findById(roleId)
            .orElseThrow(() -> new NoSuchElementException("Role not found: " + roleId));
        MetaEntity entity = entityRepository.findByName(req.entityName())
            .orElseThrow(() -> new NoSuchElementException("Entity not found: " + req.entityName()));

        // Upsert — update if exists, create if not
        MetaPermission perm = permissionRepository
            .findByRoleIdAndEntityId(roleId, entity.getId())
            .orElseGet(() -> MetaPermission.builder().role(role).entity(entity).build());

        perm.setCanRead(req.canRead());
        perm.setCanWrite(req.canWrite());
        perm.setCanDelete(req.canDelete());
        perm.setRowFilterScript(req.rowFilterScript());
        perm.setFieldMask(req.fieldMask());

        MetaPermission saved = permissionRepository.save(perm);
        log.info("Set permission for role '{}' on entity '{}': R={} W={} D={}",
                 role.getName(), entity.getName(), req.canRead(), req.canWrite(), req.canDelete());
        return saved;
    }

    @Transactional
    public void deletePermission(UUID permissionId) {
        MetaPermission perm = permissionRepository.findById(permissionId)
            .orElseThrow(() -> new NoSuchElementException("Permission not found: " + permissionId));
        permissionRepository.delete(perm);
    }
}
