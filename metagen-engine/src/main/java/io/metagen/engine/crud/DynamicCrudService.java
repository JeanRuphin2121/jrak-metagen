package io.metagen.engine.crud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.engine.expr.Condition;
import io.metagen.engine.hook.HookContext;
import io.metagen.engine.hook.HookRegistry;
import io.metagen.engine.meta.EntityFieldResolver;
import io.metagen.engine.meta.MetaModelService;
import io.metagen.engine.rbac.Access;
import io.metagen.engine.rbac.RbacEnforcer;
import io.metagen.engine.sql.SqlIdentifiers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Generic CRUD on {@code data_*} tables, driven entirely by metadata.
 *
 * <p>Request pipeline for a write:
 * <pre>
 *   RBAC check → row-level visibility → field mask → type coercion + validation
 *     → pre-save hooks → SQL → post-save hooks
 * </pre>
 * Row-level filters apply to <em>every</em> access path (list, get by id,
 * update, delete), not just to listings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicCrudService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MetaModelService           metaModel;
    private final NamedParameterJdbcTemplate jdbc;
    private final DynamicQueryBuilder        queryBuilder;
    private final RbacEnforcer               rbac;
    private final HookRegistry               hooks;

    // ── READ ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RecordPage<Map<String, Object>> list(String entityName,
                                                int page,
                                                int size,
                                                String search,
                                                String sortField,
                                                String sortDir,
                                                Map<String, String> filters,
                                                List<String> roles,
                                                String username) {
        MetaEntity entity = metaModel.entity(entityName);
        rbac.assertAllowed(roles, entity, Access.READ);

        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        var resolver = new EntityFieldResolver(entity);
        var params = new MapSqlParameterSource()
            .addValue("pageSize", safeSize)
            .addValue("offset", (long) safePage * safeSize);

        List<String> where = new ArrayList<>();
        Condition rowFilter = rbac.rowFilter(roles, entity, Access.READ);
        if (rowFilter != null) {
            where.add(rowFilter.toSql(sqlContext(params, resolver, username, "rf")));
        }
        Condition userFilter = filterCondition(entity, resolver, filters, rbac.hiddenFields(roles, entity));
        if (userFilter != null) {
            where.add(userFilter.toSql(sqlContext(params, resolver, username, "uf")));
        }

        boolean withSearch = search != null && !search.isBlank();
        if (withSearch) {
            params.addValue("search", "%" + escapeLike(search.trim()) + "%");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
            queryBuilder.buildSelect(entity, where, withSearch, sortField, sortDir), params);
        Long total = jdbc.queryForObject(
            queryBuilder.buildCount(entity, where, withSearch), params, Long.class);

        List<Map<String, Object>> content = rows.stream()
            .map(r -> rbac.applyFieldMask(toApi(r), roles, entity))
            .toList();
        return RecordPage.of(content, total != null ? total : 0L, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findById(String entityName, UUID id, List<String> roles, String username) {
        MetaEntity entity = metaModel.entity(entityName);
        rbac.assertAllowed(roles, entity, Access.READ);
        return rbac.applyFieldMask(toApi(loadRaw(entity, id, roles, username, Access.READ)), roles, entity);
    }

    // ── CREATE ────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> create(String entityName,
                                      Map<String, Object> body,
                                      List<String> roles,
                                      String username) {
        MetaEntity entity = metaModel.entity(entityName);
        rbac.assertAllowed(roles, entity, Access.WRITE);

        Map<String, Object> values = validate(entity, body, roles, true);

        var ctx = HookContext.forCreate(entity, values, username);
        runPreSave(entity, ctx, values);

        String initialState = entity.getWorkflow() == null ? null
            : entity.getWorkflow().getInitialState().map(s -> s.getName()).orElse(null);

        UUID recordId = UUID.randomUUID();
        var params = new MapSqlParameterSource()
            .addValues(ctx.getRecord())
            .addValue("id", recordId)
            .addValue("state", initialState)
            .addValue("createdBy", entity.isAuditable() ? username : null);

        Map<String, Object> stored = jdbc.queryForMap(
            queryBuilder.buildInsert(entity, ctx.getRecord().keySet()), params);

        hooks.executePostSave(HookContext.forCreate(entity, stored, username));
        log.debug("Created {}/{}", entityName, recordId);
        return rbac.applyFieldMask(toApi(stored), roles, entity);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> update(String entityName,
                                      UUID id,
                                      Map<String, Object> body,
                                      List<String> roles,
                                      String username) {
        MetaEntity entity = metaModel.entity(entityName);
        rbac.assertAllowed(roles, entity, Access.WRITE);

        // The record must be visible to the user *for writing* — row filters included.
        Map<String, Object> current = loadRaw(entity, id, roles, username, Access.WRITE);
        assertNotLocked(entity, current, roles);

        Map<String, Object> values = validate(entity, body, roles, false);

        var ctx = HookContext.forUpdate(entity, id, values, username);
        runPreSave(entity, ctx, values);

        if (ctx.getRecord().isEmpty()) {
            // Nothing writable in the payload: no-op rather than an invalid "UPDATE … SET".
            return rbac.applyFieldMask(toApi(current), roles, entity);
        }

        var params = new MapSqlParameterSource()
            .addValues(ctx.getRecord())
            .addValue("id", id)
            .addValue("updatedBy", entity.isAuditable() ? username : null);

        Map<String, Object> stored = jdbc.queryForMap(
            queryBuilder.buildUpdate(entity, ctx.getRecord().keySet()), params);

        hooks.executePostSave(HookContext.forUpdate(entity, id, stored, username));
        log.debug("Updated {}/{}", entityName, id);
        return rbac.applyFieldMask(toApi(stored), roles, entity);
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    @Transactional
    public void delete(String entityName, UUID id, List<String> roles, String username) {
        MetaEntity entity = metaModel.entity(entityName);
        rbac.assertAllowed(roles, entity, Access.DELETE);
        Map<String, Object> current = loadRaw(entity, id, roles, username, Access.DELETE);
        assertNotLocked(entity, current, roles);

        if (entity.isSoftDelete()) {
            jdbc.update(queryBuilder.buildSoftDelete(entity), new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("deletedBy", entity.isAuditable() ? username : null));
        } else {
            jdbc.update(queryBuilder.buildHardDelete(entity), new MapSqlParameterSource("id", id));
        }
        log.debug("Deleted {}/{} (soft={})", entityName, id, entity.isSoftDelete());
    }

    // ── Shared helpers (also used by the workflow engine) ─────────────────

    /**
     * Loads a record as stored, applying the row filter for the given access
     * level. A record the user may not see is reported as "not found" so its
     * existence is not disclosed.
     */
    public Map<String, Object> loadRaw(MetaEntity entity, UUID id, List<String> roles,
                                       String username, Access access) {
        var params = new MapSqlParameterSource("id", id);
        List<String> where = new ArrayList<>();
        Condition rowFilter = rbac.rowFilter(roles, entity, access);
        if (rowFilter != null) {
            where.add(rowFilter.toSql(
                sqlContext(params, new EntityFieldResolver(entity), username, "rf")));
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
            queryBuilder.buildSelectById(entity, where), params);
        if (rows.isEmpty()) {
            throw new NoSuchElementException(entity.getName() + " with id=" + id + " not found");
        }
        return rows.get(0);
    }

    /** Converts a JDBC row to its API shape (jsonb → structured JSON, no internal columns). */
    public Map<String, Object> toApi(Map<String, Object> row) {
        var out = new LinkedHashMap<String, Object>();
        row.forEach((column, value) -> {
            if ("deleted_at".equals(column)) return;
            if (value != null && "org.postgresql.util.PGobject".equals(value.getClass().getName())) {
                try {
                    out.put(column, JSON.readValue(value.toString(), Object.class));
                } catch (JsonProcessingException e) {
                    out.put(column, value.toString());
                }
            } else {
                out.put(column, value);
            }
        });
        return out;
    }

    /**
     * A record that reached a terminal workflow state (e.g. APPROVED, REJECTED) is
     * frozen: it can no longer be edited or deleted, except by ADMIN. Only the
     * workflow engine may still move it.
     */
    private void assertNotLocked(MetaEntity entity, Map<String, Object> row, List<String> roles) {
        if (entity.getWorkflow() == null || rbac.isAdmin(roles)) return;
        Object state = row.get("state");
        if (state == null) return;
        boolean terminal = entity.getWorkflow().getStates().stream()
            .anyMatch(s -> s.isTerminal() && s.getName().equals(state.toString()));
        if (terminal) {
            throw new IllegalStateException("The record is in the final state '" + state
                + "' and can no longer be modified or deleted");
        }
    }

    public static Map<String, Object> variables(String username) {
        return Map.of("user", username == null ? "" : username);
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private Condition.SqlContext sqlContext(MapSqlParameterSource params, EntityFieldResolver resolver,
                                            String username, String prefix) {
        return new Condition.SqlContext(params, resolver, variables(username), prefix);
    }

    private Condition filterCondition(MetaEntity entity, EntityFieldResolver resolver,
                                      Map<String, String> filters, Set<String> hidden) {
        if (filters == null || filters.isEmpty()) return null;
        List<Condition> parts = new ArrayList<>();
        Map<String, String> errors = new LinkedHashMap<>();
        filters.forEach((field, value) -> {
            if (!SqlIdentifiers.isSafe(field) || !resolver.exists(field) || hidden.contains(field)) {
                errors.put(field, "unknown filter field");
                return;
            }
            try {
                resolver.coerce(field, value);
                parts.add(Condition.eq(field, value));
            } catch (IllegalArgumentException e) {
                errors.put(field, e.getMessage());
            }
        });
        if (!errors.isEmpty()) throw new FieldValidationException(errors);
        return Condition.allOf(parts);
    }

    /**
     * Turns a raw JSON payload into typed column values.
     *
     * <p>System columns in the payload are ignored (clients often send back the
     * record they read); unknown fields are rejected; fields the user may not
     * write are dropped.
     */
    private Map<String, Object> validate(MetaEntity entity, Map<String, Object> body,
                                         List<String> roles, boolean creating) {
        Map<String, Object> payload = body != null ? body : Map.of();
        Set<String> blocked = rbac.readonlyFields(roles, entity);
        Map<String, MetaField> fields = new LinkedHashMap<>();
        entity.getFields().forEach(f -> fields.put(f.getName(), f));

        Map<String, String> errors = new LinkedHashMap<>();
        for (String key : payload.keySet()) {
            if (!fields.containsKey(key) && !SqlIdentifiers.isSystemColumn(key)) {
                errors.put(key, "unknown field");
            }
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (MetaField field : fields.values()) {
            String name = field.getName();
            boolean provided = payload.containsKey(name) && !blocked.contains(name);
            Object raw = provided ? payload.get(name) : null;

            if (!provided) {
                if (!creating) continue;                       // partial update: leave untouched
                if (field.getDefaultValue() == null) {
                    if (field.isRequired()) errors.put(name, "is required");
                    continue;
                }
                raw = field.getDefaultValue();
            }
            try {
                Object typed = ValueCoercer.coerce(field, raw);
                if (typed == null && field.isRequired()) {
                    errors.put(name, "is required");
                } else {
                    values.put(name, typed);
                }
            } catch (IllegalArgumentException e) {
                errors.put(name, e.getMessage());
            }
        }
        if (!errors.isEmpty()) throw new FieldValidationException(errors);
        return values;
    }

    /** Runs pre-save hooks, then re-validates any value a hook replaced. */
    private void runPreSave(MetaEntity entity, HookContext ctx, Map<String, Object> validated) {
        hooks.executePreSave(ctx);
        if (ctx.hasErrors()) {
            Map<String, String> errors = new LinkedHashMap<>(ctx.getErrors());
            if (ctx.isAborted()) errors.putIfAbsent("_record", String.valueOf(ctx.getAbortReason()));
            throw new FieldValidationException(errors);
        }
        Map<String, String> errors = new LinkedHashMap<>();
        for (var entry : ctx.getRecord().entrySet()) {
            String name = entry.getKey();
            if (validated.containsKey(name) && validated.get(name) == entry.getValue()) continue;
            try {
                MetaField field = queryBuilder.requireField(entity, name);
                entry.setValue(ValueCoercer.coerce(field, entry.getValue()));
            } catch (IllegalArgumentException e) {
                errors.put(name, e.getMessage());
            }
        }
        if (!errors.isEmpty()) throw new FieldValidationException(errors);
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
