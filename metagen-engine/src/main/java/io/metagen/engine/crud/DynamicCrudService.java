package io.metagen.engine.crud;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.repository.MetaEntityRepository;
import io.metagen.engine.hook.HookContext;
import io.metagen.engine.hook.HookRegistry;
import io.metagen.engine.rbac.RbacEnforcer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Core CRUD service for user-defined entities.
 *
 * <p>This service is the engine's heart: it translates HTTP requests into
 * parameterised SQL, enforces RBAC rules, invokes business hooks, and
 * returns plain {@code Map<String, Object>} records that the REST layer
 * serialises to JSON.</p>
 *
 * <h2>Request lifecycle (example: CREATE)</h2>
 * <pre>
 *  DynamicRestController
 *    │ POST /api/data/supplier  { "name": "Acme", "siret": "..." }
 *    ▼
 *  DynamicCrudService.create("supplier", body, principal)
 *    │
 *    ├─ 1. Load MetaEntity "supplier" (cached)
 *    ├─ 2. RbacEnforcer.assertCanWrite(user, entity)
 *    ├─ 3. RbacEnforcer.filterWritableFields(body, user, entity)
 *    ├─ 4. HookRegistry.executePreSave(ctx)         ← business hooks
 *    ├─ 5. DynamicQueryBuilder.buildInsert(entity, fields)
 *    ├─ 6. NamedParameterJdbcTemplate.queryForMap(sql, params)
 *    ├─ 7. HookRegistry.executePostSave(ctx)
 *    └─ 8. Return record Map
 * </pre>
 *
 * <h2>Caching</h2>
 * Entity metadata is cached in {@code entityMetaCache} by name.
 * The cache is evicted whenever the Studio API modifies an entity definition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicCrudService {

    private final MetaEntityRepository    metaEntityRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final DynamicQueryBuilder     queryBuilder;
    private final RbacEnforcer            rbacEnforcer;
    private final HookRegistry            hookRegistry;

    // ── LIST ─────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of records.
     *
     * @param entityName technical entity name (e.g. {@code supplier})
     * @param page       0-based page number
     * @param size       page size (max 200 enforced)
     * @param search     free-text search term, or null
     * @param sortField  field to sort by, or null (defaults to created_at DESC)
     * @param sortDir    {@code ASC} or {@code DESC}
     * @param userRoles  roles of the authenticated user
     * @return paginated records with field masking applied
     */
    @Transactional(readOnly = true)
    public RecordPage<Map<String, Object>> list(String entityName,
                                                int page,
                                                int size,
                                                String search,
                                                String sortField,
                                                String sortDir,
                                                List<String> userRoles) {
        var entity  = loadEntity(entityName);
        int safeSz  = Math.min(size, 200);
        int offset  = page * safeSz;

        rbacEnforcer.assertCanRead(userRoles, entity);
        String rowFilter = rbacEnforcer.buildRowFilter(userRoles, entity);

        // Wrap search term for ILIKE
        var params = new MapSqlParameterSource()
            .addValue("pageSize", safeSz)
            .addValue("offset",   offset);
        if (search != null && !search.isBlank()) {
            params.addValue("search", "%" + search.trim() + "%");
        }

        String selectSql = queryBuilder.buildSelect(entity, rowFilter, search, sortField, sortDir);
        String countSql  = queryBuilder.buildCount(entity, rowFilter, search);

        List<Map<String, Object>> rows = jdbc.queryForList(selectSql, params);
        Long total = jdbc.queryForObject(countSql, params, Long.class);
        long totalSafe = total != null ? total : 0L;

        List<Map<String, Object>> masked = rows.stream()
            .map(r -> rbacEnforcer.applyFieldMask(r, userRoles, entity))
            .toList();

        return RecordPage.of(masked, totalSafe, page, safeSz);
    }

    // ── GET ONE ──────────────────────────────────────────────────────

    /**
     * Returns a single record by id.
     *
     * @throws NoSuchElementException  if the record does not exist
     * @throws SecurityException       if the user lacks READ permission
     */
    @Transactional(readOnly = true)
    public Map<String, Object> findById(String entityName, UUID id, List<String> userRoles) {
        var entity = loadEntity(entityName);
        rbacEnforcer.assertCanRead(userRoles, entity);

        String sql    = queryBuilder.buildSelectById(entity);
        var    params = new MapSqlParameterSource("id", id);

        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        if (rows.isEmpty()) {
            throw new NoSuchElementException(
                entityName + " with id=" + id + " not found");
        }
        return rbacEnforcer.applyFieldMask(rows.getFirst(), userRoles, entity);
    }

    // ── CREATE ───────────────────────────────────────────────────────

    /**
     * Inserts a new record.
     *
     * @param entityName technical entity name
     * @param body       field values from the request body
     * @param userRoles  roles of the authenticated user
     * @param username   login name injected into auditing columns
     * @return the created record (as returned by RETURNING *)
     */
    @Transactional
    public Map<String, Object> create(String entityName,
                                      Map<String, Object> body,
                                      List<String> userRoles,
                                      String username) {
        var entity   = loadEntity(entityName);
        rbacEnforcer.assertCanWrite(userRoles, entity);

        // Strip fields the user is not allowed to write
        var writableBody = rbacEnforcer.filterWritableFields(body, userRoles, entity);

        // Pre-save hook
        var ctx = HookContext.forCreate(entity, writableBody, username);
        hookRegistry.executePreSave(ctx);
        if (ctx.hasErrors()) {
            throw new IllegalArgumentException("Validation errors: " + ctx.getErrors());
        }

        // Determine initial workflow state
        String initialState = null;
        if (entity.getWorkflow() != null) {
            initialState = entity.getWorkflow().getInitialState()
                .map(s -> s.getName())
                .orElse(null);
        }

        var recordId = UUID.randomUUID();
        var params = new MapSqlParameterSource()
            .addValues(ctx.getRecord())
            .addValue("id",        recordId)
            .addValue("state",     initialState)
            .addValue("createdBy", username);

        String sql    = queryBuilder.buildInsert(entity, ctx.getRecord().keySet());
        var    result = jdbc.queryForMap(sql, params);

        // Post-save hook
        hookRegistry.executePostSave(HookContext.forCreate(entity, result, username));

        log.debug("Created {}/{}", entityName, recordId);
        return result;
    }

    // ── UPDATE ───────────────────────────────────────────────────────

    /**
     * Updates an existing record (partial update — only provided fields are changed).
     *
     * @throws NoSuchElementException if the record does not exist
     */
    @Transactional
    public Map<String, Object> update(String entityName,
                                      UUID id,
                                      Map<String, Object> body,
                                      List<String> userRoles,
                                      String username) {
        var entity = loadEntity(entityName);
        rbacEnforcer.assertCanWrite(userRoles, entity);

        // Verify record exists
        findById(entityName, id, userRoles);

        var writableBody = rbacEnforcer.filterWritableFields(body, userRoles, entity);

        var ctx = HookContext.forUpdate(entity, id, writableBody, username);
        hookRegistry.executePreSave(ctx);
        if (ctx.hasErrors()) {
            throw new IllegalArgumentException("Validation errors: " + ctx.getErrors());
        }

        var params = new MapSqlParameterSource()
            .addValues(ctx.getRecord())
            .addValue("id",        id)
            .addValue("updatedBy", username);

        String sql    = queryBuilder.buildUpdate(entity, ctx.getRecord().keySet());
        var    result = jdbc.queryForMap(sql, params);

        hookRegistry.executePostSave(HookContext.forUpdate(entity, id, result, username));

        log.debug("Updated {}/{}", entityName, id);
        return result;
    }

    // ── DELETE ───────────────────────────────────────────────────────

    /**
     * Deletes a record (soft or hard depending on entity config).
     *
     * @throws NoSuchElementException if the record does not exist
     */
    @Transactional
    public void delete(String entityName, UUID id, List<String> userRoles, String username) {
        var entity = loadEntity(entityName);
        rbacEnforcer.assertCanDelete(userRoles, entity);

        // Verify exists first (also applies row-level filter)
        findById(entityName, id, userRoles);

        if (entity.isSoftDelete()) {
            var params = new MapSqlParameterSource()
                .addValue("id",        id)
                .addValue("deletedBy", username);
            jdbc.update(queryBuilder.buildSoftDelete(entity), params);
        } else {
            jdbc.update(queryBuilder.buildHardDelete(entity),
                        new MapSqlParameterSource("id", id));
        }
        log.debug("Deleted {}/{} (soft={})", entityName, id, entity.isSoftDelete());
    }

    // ── Cache management ─────────────────────────────────────────────

    /**
     * Loads entity metadata from cache (or DB on miss).
     * Cache key is the entity's technical name.
     */
    @Cacheable(cacheNames = "entityMeta", key = "#name")
    public MetaEntity loadEntity(String name) {
        return metaEntityRepository.findByNameWithAll(name)
            .orElseThrow(() -> new NoSuchElementException("Unknown entity: " + name));
    }

    /** Evicts the cached metadata when the Studio modifies an entity. */
    @CacheEvict(cacheNames = "entityMeta", key = "#name")
    public void evictCache(String name) {
        log.debug("Evicted entityMeta cache for '{}'", name);
    }
}
