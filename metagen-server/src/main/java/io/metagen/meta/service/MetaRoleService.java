package io.metagen.meta.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaPermission;
import io.metagen.core.domain.MetaRole;
import io.metagen.core.repository.MetaEntityRepository;
import io.metagen.core.repository.MetaPermissionRepository;
import io.metagen.core.repository.MetaRoleRepository;
import io.metagen.engine.config.MetaYamlCodec;
import io.metagen.engine.expr.Condition;
import io.metagen.engine.meta.EntityFieldResolver;
import io.metagen.engine.meta.MetaModelService;
import io.metagen.engine.meta.NamingRules;
import io.metagen.meta.dto.CreatePermissionRequest;
import io.metagen.meta.dto.CreateRoleRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetaRoleService {

    private final MetaRoleRepository       roleRepository;
    private final MetaEntityRepository     entityRepository;
    private final MetaPermissionRepository permissionRepository;
    private final MetaModelService         metaModel;

    @Transactional(readOnly = true)
    public List<MetaRole> listRoles() {
        return roleRepository.findAll().stream()
            .sorted((a, b) -> a.getName().compareTo(b.getName()))
            .toList();
    }

    @Transactional
    public MetaRole createRole(CreateRoleRequest req) {
        NamingRules.roleName(req.name()).ifPresent(m -> { throw new IllegalArgumentException(m); });
        if (roleRepository.existsByName(req.name())) {
            throw new IllegalArgumentException("Role '" + req.name() + "' already exists");
        }
        MetaRole saved = roleRepository.save(MetaRole.builder()
            .name(req.name()).label(req.label()).description(req.description()).builtIn(false).build());
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
        metaModel.evictAll();
        log.info("Deleted role '{}'", role.getName());
    }

    @Transactional(readOnly = true)
    public List<MetaPermission> listPermissions(UUID roleId) {
        if (!roleRepository.existsById(roleId)) throw new NoSuchElementException("Role not found: " + roleId);
        return permissionRepository.findByRoleId(roleId);
    }

    @Transactional
    public MetaPermission setPermission(UUID roleId, CreatePermissionRequest req) {
        MetaRole role = roleRepository.findById(roleId)
            .orElseThrow(() -> new NoSuchElementException("Role not found: " + roleId));
        MetaEntity entity = entityRepository.findByNameWithAll(req.entityName())
            .orElseThrow(() -> new NoSuchElementException("Entity not found: " + req.entityName()));

        var resolver = new EntityFieldResolver(entity);
        Condition filter = Condition.parse(req.rowFilter());
        if (filter != null) filter.validate(resolver);
        validateMask(req.fieldMask(), resolver);

        MetaPermission perm = permissionRepository.findByRoleIdAndEntityId(roleId, entity.getId())
            .orElseGet(() -> MetaPermission.builder().role(role).entity(entity).build());
        perm.setCanRead(req.canRead());
        perm.setCanWrite(req.canWrite());
        perm.setCanDelete(req.canDelete());
        perm.setRowFilter(MetaYamlCodec.toJson(req.rowFilter()));
        perm.setFieldMask(req.fieldMask() != null && !req.fieldMask().isEmpty() ? req.fieldMask() : null);

        MetaPermission saved = permissionRepository.save(perm);
        metaModel.evictAll();
        log.info("Set permission for role '{}' on '{}': R={} W={} D={}",
                 role.getName(), entity.getName(), req.canRead(), req.canWrite(), req.canDelete());
        return saved;
    }

    @Transactional
    public void deletePermission(UUID roleId, UUID permissionId) {
        MetaPermission perm = permissionRepository.findById(permissionId)
            .filter(p -> p.getRole().getId().equals(roleId))
            .orElseThrow(() -> new NoSuchElementException("Permission not found: " + permissionId));
        permissionRepository.delete(perm);
        metaModel.evictAll();
    }

    private void validateMask(JsonNode mask, EntityFieldResolver resolver) {
        if (mask == null || mask.isNull() || mask.isEmpty()) return;
        if (!mask.isObject()) throw new IllegalArgumentException("fieldMask must be {hidden: [...], readonly: [...]}");
        mask.fieldNames().forEachRemaining(key -> {
            if (!key.equals("hidden") && !key.equals("readonly")) {
                throw new IllegalArgumentException("fieldMask has unknown key '" + key + "'");
            }
            JsonNode names = mask.get(key);
            if (!names.isArray()) throw new IllegalArgumentException("fieldMask." + key + " must be a list");
            names.forEach(n -> {
                if (!resolver.exists(n.asText())) {
                    throw new IllegalArgumentException(
                        "fieldMask." + key + " references unknown field '" + n.asText() + "'");
                }
            });
        });
    }
}
