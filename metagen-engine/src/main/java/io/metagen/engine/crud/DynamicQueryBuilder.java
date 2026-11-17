package io.metagen.engine.crud;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.core.domain.MetaFieldType;
import io.metagen.engine.sql.SqlIdentifiers;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.metagen.engine.sql.SqlIdentifiers.quote;

/**
 * Builds the SQL statements executed on {@code data_*} tables.
 *
 * <p>Rules that make the output safe by construction:
 * <ul>
 *   <li>every identifier goes through {@link SqlIdentifiers#quote};</li>
 *   <li>every value is a named bind parameter;</li>
 *   <li>extra WHERE fragments are only ever produced by
 *       {@code Condition.toSql}, never taken from metadata as text.</li>
 * </ul>
 *
 * <p>Every data table carries the full set of system columns
 * ({@link SqlIdentifiers#SYSTEM_COLUMNS}) regardless of the entity's flags, so
 * the generated SQL does not depend on how the entity was configured when its
 * table was first deployed.
 */
@Component
public class DynamicQueryBuilder {

    public String table(MetaEntity entity) {
        return quote(entity.getTableName());
    }

    /** {@code whereFragments} must come from {@code Condition.toSql}. */
    public String buildSelect(MetaEntity entity,
                              List<String> whereFragments,
                              boolean withSearch,
                              String sortField,
                              String sortDir) {
        return "SELECT " + columnList(entity) +
               " FROM " + table(entity) +
               where(entity, whereFragments, withSearch) +
               " ORDER BY " + quote(resolvedSortColumn(entity, sortField)) +
               ("ASC".equalsIgnoreCase(sortDir) ? " ASC" : " DESC") +
               ", \"id\" ASC" +
               " LIMIT :pageSize OFFSET :offset";
    }

    public String buildCount(MetaEntity entity, List<String> whereFragments, boolean withSearch) {
        return "SELECT COUNT(*) FROM " + table(entity) + where(entity, whereFragments, withSearch);
    }

    public String buildSelectById(MetaEntity entity, List<String> whereFragments) {
        var sb = new StringBuilder("SELECT " + columnList(entity) +
                                   " FROM " + table(entity) +
                                   " WHERE \"id\" = :id AND \"deleted_at\" IS NULL");
        whereFragments.forEach(f -> sb.append(" AND ").append(f));
        return sb.toString();
    }

    public String buildInsert(MetaEntity entity, Collection<String> fieldNames) {
        var cols = new ArrayList<String>(List.of("\"id\"", "\"state\"", "\"created_by\"", "\"updated_by\""));
        var vals = new ArrayList<String>(List.of(":id", ":state", ":createdBy", ":createdBy"));
        for (String name : fieldNames) {
            MetaField field = requireField(entity, name);
            cols.add(quote(name));
            vals.add(placeholder(field));
        }
        return "INSERT INTO " + table(entity) +
               " (" + String.join(", ", cols) + ")" +
               " VALUES (" + String.join(", ", vals) + ")" +
               " RETURNING " + columnList(entity);
    }

    public String buildUpdate(MetaEntity entity, Collection<String> fieldNames) {
        var set = new ArrayList<String>();
        for (String name : fieldNames) {
            MetaField field = requireField(entity, name);
            set.add(quote(name) + " = " + placeholder(field));
        }
        set.add("\"updated_at\" = NOW()");
        set.add("\"updated_by\" = :updatedBy");
        return "UPDATE " + table(entity) +
               " SET " + String.join(", ", set) +
               " WHERE \"id\" = :id AND \"deleted_at\" IS NULL" +
               " RETURNING " + columnList(entity);
    }

    public String buildSoftDelete(MetaEntity entity) {
        return "UPDATE " + table(entity) +
               " SET \"deleted_at\" = NOW(), \"updated_at\" = NOW(), \"updated_by\" = :deletedBy" +
               " WHERE \"id\" = :id AND \"deleted_at\" IS NULL";
    }

    public String buildHardDelete(MetaEntity entity) {
        return "DELETE FROM " + table(entity) + " WHERE \"id\" = :id";
    }

    /**
     * Compare-and-set on the state column: the update only applies if the record
     * is still in the state the caller observed, which makes concurrent
     * transitions on the same record safe.
     */
    public String buildStateUpdate(MetaEntity entity) {
        return "UPDATE " + table(entity) +
               " SET \"state\" = :newState, \"updated_at\" = NOW(), \"updated_by\" = :updatedBy" +
               " WHERE \"id\" = :id AND \"deleted_at\" IS NULL" +
               " AND \"state\" IS NOT DISTINCT FROM CAST(:expectedState AS VARCHAR)" +
               " RETURNING " + columnList(entity);
    }

    public String columnList(MetaEntity entity) {
        var cols = new ArrayList<String>();
        SqlIdentifiers.SYSTEM_COLUMNS.forEach(c -> cols.add(quote(c)));
        entity.getFields().forEach(f -> cols.add(quote(f.getName())));
        return String.join(", ", cols);
    }

    public boolean hasSearchableFields(MetaEntity entity) {
        return entity.getFields().stream().anyMatch(this::isSearchable);
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private String where(MetaEntity entity, List<String> whereFragments, boolean withSearch) {
        var sb = new StringBuilder(" WHERE \"deleted_at\" IS NULL");
        whereFragments.forEach(f -> sb.append(" AND ").append(f));
        if (withSearch) {
            sb.append(" AND (").append(searchClause(entity)).append(")");
        }
        return sb.toString();
    }

    private String searchClause(MetaEntity entity) {
        var clauses = entity.getFields().stream()
            .filter(this::isSearchable)
            .map(f -> quote(f.getName()) + " ILIKE :search")
            .toList();
        return clauses.isEmpty() ? "FALSE" : String.join(" OR ", clauses);
    }

    private boolean isSearchable(MetaField f) {
        if (!f.isSearchable()) return false;
        MetaFieldType t = f.getType();
        return t == MetaFieldType.STRING || t == MetaFieldType.TEXT || t == MetaFieldType.RICH_TEXT
            || t == MetaFieldType.EMAIL || t == MetaFieldType.URL || t == MetaFieldType.PHONE
            || t == MetaFieldType.ENUM;
    }

    /** Unknown or missing sort fields fall back to creation date — never to user input. */
    private String resolvedSortColumn(MetaEntity entity, String sortField) {
        if (sortField == null) return "created_at";
        boolean valid = SqlIdentifiers.isSystemColumn(sortField)
            || entity.getFields().stream().anyMatch(f -> f.getName().equals(sortField));
        return valid ? sortField : "created_at";
    }

    private String placeholder(MetaField field) {
        String param = ":" + field.getName();
        return ValueCoercer.isJsonType(field.getType()) ? "CAST(" + param + " AS jsonb)" : param;
    }

    MetaField requireField(MetaEntity entity, String name) {
        return entity.getFields().stream()
            .filter(f -> f.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Field '" + name + "' does not exist on entity '" + entity.getName() + "'"));
    }
}
