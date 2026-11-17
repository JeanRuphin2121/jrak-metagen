package io.metagen.engine.rbac;

import com.fasterxml.jackson.databind.JsonNode;
import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaPermission;
import io.metagen.engine.expr.Condition;
import io.metagen.engine.meta.MetaModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies role-based access control to runtime (data) requests.
 *
 * <p>Semantics when a user holds several roles — the most permissive grant wins:
 * <ul>
 *   <li>an access level is allowed if <em>any</em> role grants it;</li>
 *   <li>row filters of the roles granting that level are OR-ed, and a grant
 *       without a filter lifts the restriction entirely;</li>
 *   <li>a field is hidden / read-only only if <em>every</em> granting role
 *       hides / locks it.</li>
 * </ul>
 * The built-in {@code ADMIN} role bypasses all checks.
 */
@Component
@RequiredArgsConstructor
public class RbacEnforcer {

    public static final String ADMIN_ROLE = "ADMIN";

    private final MetaModelService metaModel;

    public void assertAllowed(List<String> roles, MetaEntity entity, Access access) {
        if (isAdmin(roles)) return;
        if (granting(roles, entity, access).isEmpty()) {
            throw new SecurityException(
                "You do not have " + access + " access on '" + entity.getName() + "'");
        }
    }

    public boolean isAllowed(List<String> roles, MetaEntity entity, Access access) {
        return isAdmin(roles) || !granting(roles, entity, access).isEmpty();
    }

    /**
     * Row-level restriction for the given access level.
     *
     * @return {@code null} when the user is unrestricted
     */
    public Condition rowFilter(List<String> roles, MetaEntity entity, Access access) {
        if (isAdmin(roles)) return null;
        List<Condition> filters = new ArrayList<>();
        for (MetaPermission perm : granting(roles, entity, access)) {
            Condition c = Condition.parse(perm.getRowFilter());
            if (c == null) return null;          // one unrestricted grant is enough
            filters.add(c);
        }
        return Condition.anyOf(filters);
    }

    public Set<String> hiddenFields(List<String> roles, MetaEntity entity) {
        if (isAdmin(roles)) return Set.of();
        return intersect(granting(roles, entity, Access.READ), "hidden");
    }

    /** Fields the user may not write: read-only or hidden for every write-granting role. */
    public Set<String> readonlyFields(List<String> roles, MetaEntity entity) {
        if (isAdmin(roles)) return Set.of();
        List<MetaPermission> writers = granting(roles, entity, Access.WRITE);
        Set<String> blocked = null;
        for (MetaPermission perm : writers) {
            Set<String> own = new HashSet<>(maskEntries(perm, "readonly"));
            own.addAll(maskEntries(perm, "hidden"));
            if (blocked == null) blocked = own; else blocked.retainAll(own);
        }
        return blocked == null ? Set.of() : blocked;
    }

    public EntityAccess describe(List<String> roles, MetaEntity entity) {
        return new EntityAccess(
            isAllowed(roles, entity, Access.READ),
            isAllowed(roles, entity, Access.WRITE),
            isAllowed(roles, entity, Access.DELETE),
            hiddenFields(roles, entity),
            readonlyFields(roles, entity));
    }

    /** Removes the fields the user may not see. */
    public Map<String, Object> applyFieldMask(Map<String, Object> record,
                                              List<String> roles,
                                              MetaEntity entity) {
        Set<String> hidden = hiddenFields(roles, entity);
        if (hidden.isEmpty()) return record;
        var result = new LinkedHashMap<>(record);
        hidden.forEach(result::remove);
        return result;
    }

    public boolean isAdmin(List<String> roles) {
        return roles != null && roles.contains(ADMIN_ROLE);
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private List<MetaPermission> granting(List<String> roles, MetaEntity entity, Access access) {
        return metaModel.permissions(roles, entity.getName()).stream()
            .filter(p -> switch (access) {
                case READ   -> p.isCanRead();
                case WRITE  -> p.isCanWrite();
                case DELETE -> p.isCanDelete();
            })
            .toList();
    }

    private Set<String> intersect(List<MetaPermission> perms, String key) {
        Set<String> result = null;
        for (MetaPermission perm : perms) {
            Set<String> own = maskEntries(perm, key);
            if (result == null) result = new HashSet<>(own); else result.retainAll(own);
        }
        return result == null ? Set.of() : result;
    }

    private Set<String> maskEntries(MetaPermission perm, String key) {
        Set<String> out = new HashSet<>();
        JsonNode mask = perm.getFieldMask();
        if (mask != null && mask.has(key) && mask.get(key).isArray()) {
            mask.get(key).forEach(n -> out.add(n.asText()));
        }
        return out;
    }
}
