package io.metagen.engine.crud;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.core.domain.MetaFieldType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Builds parameterised SQL statements from {@link MetaEntity} metadata.
 *
 * <h2>SQL injection prevention</h2>
 * <ul>
 *   <li>Column names come from validated {@link MetaField#getName()} values,
 *       constrained by the {@code ^[a-z][a-z0-9_]*$} pattern — safe to embed.</li>
 *   <li>Table names follow the same pattern via {@link MetaEntity#getTableName()}.</li>
 *   <li>User-supplied <em>values</em> are always passed as Spring named
 *       parameters ({@code :paramName}) — never string-interpolated.</li>
 *   <li>Sort field is validated against the entity's declared field list before
 *       embedding in the ORDER BY clause.</li>
 * </ul>
 *
 * <h2>Generated table structure</h2>
 * Every {@code data_*} table has these fixed system columns in addition to
 * the declared {@link MetaField fields}:
 * <ul>
 *   <li>{@code id}          — UUID primary key</li>
 *   <li>{@code created_at}  — set on INSERT (when auditable)</li>
 *   <li>{@code updated_at}  — set on UPDATE (when auditable)</li>
 *   <li>{@code created_by}  — set on INSERT (when auditable)</li>
 *   <li>{@code updated_by}  — set on UPDATE (when auditable)</li>
 *   <li>{@code deleted_at}  — set on soft DELETE (when softDelete)</li>
 * </ul>
 */
@Component
public class DynamicQueryBuilder {

    // ── SELECT ───────────────────────────────────────────────────────

    /**
     * Builds a paginated SELECT with optional search, sort, and row filter.
     *
     * @param entity     the entity metadata
     * @param rowFilter  SQL WHERE fragment from the RBAC engine (may be null)
     * @param search     free-text search term (may be null)
     * @param sortField  field name to sort by (validated against entity fields)
     * @param sortDir    {@code "ASC"} or {@code "DESC"}
     * @return SQL string with {@code :pageSize}, {@code :offset},
     *         and optionally {@code :search} named parameters
     */
    public String buildSelect(MetaEntity entity,
                              String rowFilter,
                              String search,
                              String sortField,
                              String sortDir) {
        var sb = new StringBuilder();
        sb.append("SELECT ").append(columnList(entity));
        sb.append(" FROM ").append(entity.getTableName());
        sb.append(" WHERE 1=1");

        if (entity.isSoftDelete()) {
            sb.append(" AND deleted_at IS NULL");
        }
        if (rowFilter != null && !rowFilter.isBlank()) {
            sb.append(" AND (").append(rowFilter).append(")");
        }
        if (search != null && !search.isBlank()) {
            sb.append(" AND (").append(buildSearchClause(entity)).append(")");
        }

        sb.append(" ORDER BY ").append(resolvedSortColumn(entity, sortField));
        sb.append(" ").append("DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC");
        sb.append(" LIMIT :pageSize OFFSET :offset");
        return sb.toString();
    }

    /** Builds the COUNT(*) query matching the same WHERE conditions. */
    public String buildCount(MetaEntity entity, String rowFilter, String search) {
        var sb = new StringBuilder();
        sb.append("SELECT COUNT(*) FROM ").append(entity.getTableName());
        sb.append(" WHERE 1=1");

        if (entity.isSoftDelete()) {
            sb.append(" AND deleted_at IS NULL");
        }
        if (rowFilter != null && !rowFilter.isBlank()) {
            sb.append(" AND (").append(rowFilter).append(")");
        }
        if (search != null && !search.isBlank()) {
            sb.append(" AND (").append(buildSearchClause(entity)).append(")");
        }
        return sb.toString();
    }

    /** Builds {@code SELECT ... FROM ... WHERE id = :id}. */
    public String buildSelectById(MetaEntity entity) {
        return "SELECT " + columnList(entity) +
               " FROM " + entity.getTableName() +
               " WHERE id = :id" +
               (entity.isSoftDelete() ? " AND deleted_at IS NULL" : "");
    }

    // ── INSERT ───────────────────────────────────────────────────────

    /**
     * Builds an INSERT statement for the given field names.
     * System columns (id, created_at, created_by) are injected automatically.
     *
     * @param entity     entity metadata
     * @param fieldNames declared fields present in the request body
     * @return SQL with named parameters matching each field name
     *         plus {@code :id}, {@code :createdBy}
     */
    public String buildInsert(MetaEntity entity, Collection<String> fieldNames) {
        var cols = new ArrayList<String>();
        var vals = new ArrayList<String>();

        cols.add("id");         vals.add(":id");
        cols.add("state");      vals.add(":state"); // workflow state

        for (var name : fieldNames) {
            validateFieldName(name, entity);
            cols.add(name);
            vals.add(":" + name);
        }

        if (entity.isAuditable()) {
            cols.add("created_at");  vals.add("NOW()");
            cols.add("updated_at");  vals.add("NOW()");
            cols.add("created_by");  vals.add(":createdBy");
            cols.add("updated_by");  vals.add(":createdBy");
        }

        return "INSERT INTO " + entity.getTableName() +
               " (" + String.join(", ", cols) + ")" +
               " VALUES (" + String.join(", ", vals) + ")" +
               " RETURNING " + columnList(entity);
    }

    // ── UPDATE ───────────────────────────────────────────────────────

    /**
     * Builds an UPDATE statement for the given field names.
     *
     * @param entity     entity metadata
     * @param fieldNames fields present in the request body (readonly fields
     *                   must be filtered out by the RBAC layer before calling)
     * @return SQL with named parameters
     */
    public String buildUpdate(MetaEntity entity, Collection<String> fieldNames) {
        var setClauses = new ArrayList<String>();

        for (var name : fieldNames) {
            validateFieldName(name, entity);
            setClauses.add(name + " = :" + name);
        }

        if (entity.isAuditable()) {
            setClauses.add("updated_at = NOW()");
            setClauses.add("updated_by = :updatedBy");
        }

        return "UPDATE " + entity.getTableName() +
               " SET " + String.join(", ", setClauses) +
               " WHERE id = :id" +
               " RETURNING " + columnList(entity);
    }

    // ── DELETE ───────────────────────────────────────────────────────

    /** Soft delete — sets {@code deleted_at}. */
    public String buildSoftDelete(MetaEntity entity) {
        return "UPDATE " + entity.getTableName() +
               " SET deleted_at = NOW()" +
               (entity.isAuditable() ? ", updated_by = :deletedBy" : "") +
               " WHERE id = :id AND deleted_at IS NULL";
    }

    /** Hard delete — physically removes the row. */
    public String buildHardDelete(MetaEntity entity) {
        return "DELETE FROM " + entity.getTableName() + " WHERE id = :id";
    }

    // ── Workflow state update ─────────────────────────────────────────

    /** Updates only the {@code state} column (used by the workflow engine). */
    public String buildStateUpdate(MetaEntity entity) {
        return "UPDATE " + entity.getTableName() +
               " SET state = :newState" +
               (entity.isAuditable() ? ", updated_at = NOW(), updated_by = :updatedBy" : "") +
               " WHERE id = :id" +
               " RETURNING " + columnList(entity);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Returns the comma-separated list of all readable columns for this entity.
     * System columns (id, auditing, soft-delete) are included automatically.
     */
    public String columnList(MetaEntity entity) {
        var cols = new ArrayList<String>();
        cols.add("id");
        if (entity.getWorkflow() != null) {
            cols.add("state");
        }
        entity.getFields().stream()
              .map(MetaField::getName)
              .forEach(cols::add);
        if (entity.isAuditable()) {
            cols.addAll(List.of("created_at", "updated_at", "created_by", "updated_by"));
        }
        if (entity.isSoftDelete()) {
            cols.add("deleted_at");
        }
        return String.join(", ", cols);
    }

    /**
     * Builds an ILIKE search clause across all searchable STRING/TEXT fields.
     * Returns {@code "FALSE"} if no searchable text field exists.
     */
    private String buildSearchClause(MetaEntity entity) {
        var clauses = entity.getFields().stream()
            .filter(MetaField::isSearchable)
            .filter(f -> isTextType(f.getType()))
            .map(f -> f.getName() + " ILIKE :search")
            .toList();
        return clauses.isEmpty() ? "FALSE" : String.join(" OR ", clauses);
    }

    private boolean isTextType(MetaFieldType type) {
        return switch (type) {
            case STRING, TEXT, RICH_TEXT, EMAIL, URL, PHONE -> true;
            default -> false;
        };
    }

    /**
     * Validates and returns the sort column name.
     * Falls back to {@code created_at} for unknown fields to prevent injection.
     */
    private String resolvedSortColumn(MetaEntity entity, String sortField) {
        if (sortField == null) return "created_at";
        boolean valid = "id".equals(sortField) ||
                        "created_at".equals(sortField) ||
                        "updated_at".equals(sortField) ||
                        entity.getFields().stream().anyMatch(f -> f.getName().equals(sortField));
        return valid ? sortField : "created_at";
    }

    /**
     * Throws if {@code name} is not a declared field name.
     * Called before embedding any caller-supplied name into SQL.
     */
    void validateFieldName(String name, MetaEntity entity) {
        boolean valid = entity.getFields().stream().anyMatch(f -> f.getName().equals(name));
        if (!valid) {
            throw new IllegalArgumentException(
                "Field '" + name + "' does not exist on entity '" + entity.getName() + "'");
        }
    }
}
