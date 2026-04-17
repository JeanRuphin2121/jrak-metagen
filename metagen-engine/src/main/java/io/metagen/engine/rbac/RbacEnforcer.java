package io.metagen.engine.rbac;

import com.fasterxml.jackson.databind.JsonNode;
import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaPermission;
import io.metagen.core.repository.MetaPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.StreamSupport;

/**
 * RBAC (Role-Based Access Control) enforcement for the runtime engine.
 *
 * <h2>How permissions work</h2>
 * <ol>
 *   <li>The JWT contains the user's role names (e.g. {@code ["BUYER", "USER"]}).</li>
 *   <li>On each request, the enforcer looks up all {@link MetaPermission} rows
 *       matching any of those roles for the target entity.</li>
 *   <li>Permissions are <em>additive</em>: having at least one role that
 *       grants {@code canRead} is sufficient to read.</li>
 *   <li>Row-level filter scripts from all matching permissions are combined
 *       with {@code OR}, so a user sees records accessible by <em>any</em>
 *       of their roles.</li>
 *   <li>Field masking is the union of hidden/readonly sets across all roles.</li>
 * </ol>
 *
 * <h2>ADMIN bypass</h2>
 * Users with the {@code ADMIN} role bypass all permission checks and see
 * all fields and all rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacEnforcer {

    private static final String ADMIN_ROLE = "ADMIN";

    private final MetaPermissionRepository permissionRepository;

    // ── Assert methods ────────────────────────────────────────────────

    public void assertCanRead(List<String> roles, MetaEntity entity) {
        if (isAdmin(roles)) return;
        boolean allowed = resolve(roles, entity).stream().anyMatch(MetaPermission::isCanRead);
        if (!allowed) throw new SecurityException(
            "Role(s) " + roles + " do not have READ on '" + entity.getName() + "'");
    }

    public void assertCanWrite(List<String> roles, MetaEntity entity) {
        if (isAdmin(roles)) return;
        boolean allowed = resolve(roles, entity).stream().anyMatch(MetaPermission::isCanWrite);
        if (!allowed) throw new SecurityException(
            "Role(s) " + roles + " do not have WRITE on '" + entity.getName() + "'");
    }

    public void assertCanDelete(List<String> roles, MetaEntity entity) {
        if (isAdmin(roles)) return;
        boolean allowed = resolve(roles, entity).stream().anyMatch(MetaPermission::isCanDelete);
        if (!allowed) throw new SecurityException(
            "Role(s) " + roles + " do not have DELETE on '" + entity.getName() + "'");
    }

    // ── Row filter ────────────────────────────────────────────────────

    /**
     * Builds a SQL WHERE fragment combining all row-filter scripts for the user's roles.
     *
     * <p>Multiple scripts are joined with {@code OR} so the user sees any row
     * accessible by at least one of their roles.</p>
     *
     * @return SQL fragment (never null; empty string means no filter)
     */
    public String buildRowFilter(List<String> roles, MetaEntity entity) {
        if (isAdmin(roles)) return "";

        var fragments = resolve(roles, entity).stream()
            .map(MetaPermission::getRowFilterScript)
            .filter(s -> s != null && !s.isBlank())
            .distinct()
            .toList();

        if (fragments.isEmpty()) return "";
        if (fragments.size() == 1) return fragments.getFirst();
        return fragments.stream()
                        .map(f -> "(" + f + ")")
                        .reduce((a, b) -> a + " OR " + b)
                        .orElse("");
    }

    // ── Field masking ─────────────────────────────────────────────────

    /**
     * Removes hidden fields from a record map.
     * Fields marked as hidden are stripped; the record is returned as-is
     * otherwise.
     *
     * @param record  the raw record from the database
     * @param roles   the user's roles
     * @param entity  the entity metadata
     * @return a new map with hidden fields removed
     */
    public Map<String, Object> applyFieldMask(Map<String, Object> record,
                                               List<String> roles,
                                               MetaEntity entity) {
        if (isAdmin(roles)) return record;

        Set<String> hiddenFields = collectHiddenFields(roles, entity);
        if (hiddenFields.isEmpty()) return record;

        var result = new LinkedHashMap<>(record);
        hiddenFields.forEach(result::remove);
        return result;
    }

    /**
     * Filters a request body to only keep fields the user is allowed to write.
     * Readonly fields are silently dropped (not an error — just ignored).
     *
     * @param body    original request body
     * @param roles   the user's roles
     * @param entity  the entity metadata
     * @return filtered map with readonly/hidden fields removed
     */
    public Map<String, Object> filterWritableFields(Map<String, Object> body,
                                                     List<String> roles,
                                                     MetaEntity entity) {
        if (isAdmin(roles)) return body;

        Set<String> readonlyFields = collectReadonlyFields(roles, entity);
        Set<String> hiddenFields   = collectHiddenFields(roles, entity);
        Set<String> blocked        = new HashSet<>();
        blocked.addAll(readonlyFields);
        blocked.addAll(hiddenFields);

        if (blocked.isEmpty()) return body;

        var result = new LinkedHashMap<>(body);
        blocked.forEach(result::remove);
        return result;
    }

    // ── Permission resolution (cached) ────────────────────────────────

    /**
     * Resolves all permissions for the given roles and entity.
     * Cached per (sorted role list + entity name) pair.
     */
    @Cacheable(cacheNames = "rbacPermissions",
               key = "#roles.stream().sorted().toList().toString() + ':' + #entity.name")
    public List<MetaPermission> resolve(List<String> roles, MetaEntity entity) {
        if (roles.isEmpty()) return List.of();
        return permissionRepository.findByRoleNamesAndEntityName(roles, entity.getName());
    }

    // ── Private helpers ───────────────────────────────────────────────

    private boolean isAdmin(List<String> roles) {
        return roles.contains(ADMIN_ROLE);
    }

    private Set<String> collectHiddenFields(List<String> roles, MetaEntity entity) {
        return collectFieldsByMaskKey(roles, entity, "hidden");
    }

    private Set<String> collectReadonlyFields(List<String> roles, MetaEntity entity) {
        return collectFieldsByMaskKey(roles, entity, "readonly");
    }

    private Set<String> collectFieldsByMaskKey(List<String> roles,
                                                MetaEntity entity,
                                                String key) {
        var result = new HashSet<String>();
        for (MetaPermission perm : resolve(roles, entity)) {
            JsonNode mask = perm.getFieldMask();
            if (mask != null && mask.has(key)) {
                StreamSupport.stream(mask.get(key).spliterator(), false)
                    .map(JsonNode::asText)
                    .forEach(result::add);
            }
        }
        return result;
    }
}
