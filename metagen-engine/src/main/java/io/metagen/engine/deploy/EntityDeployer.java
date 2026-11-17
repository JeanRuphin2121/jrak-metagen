package io.metagen.engine.deploy;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.core.domain.MetaFieldType;
import io.metagen.engine.meta.MetaModelService;
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

import static io.metagen.engine.sql.SqlIdentifiers.quote;

/**
 * Brings a {@code data_*} table in line with its entity definition.
 *
 * <p>Deployment is <strong>additive and idempotent</strong>: it creates the
 * table, adds missing columns, unique indexes and foreign keys — and never
 * drops or rewrites anything. Whatever it cannot reconcile safely (a column
 * whose type changed, a column no longer in the model) is reported as a
 * warning for a human to handle, instead of being silently ignored.
 *
 * <p>Design choices:
 * <ul>
 *   <li>all system columns exist on every table, whatever the entity flags,
 *       so attaching a workflow or enabling soft-delete later needs no DDL;</li>
 *   <li>business columns are nullable in the database — "required" is
 *       enforced by the engine, which lets a required field be added to a
 *       table that already holds rows;</li>
 *   <li>uniqueness uses a partial index ignoring soft-deleted rows;</li>
 *   <li>default values are applied by the engine at insert time, never
 *       interpolated into DDL.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDeployer {

    private static final Map<String, String> SYSTEM_COLUMN_DDL = systemColumns();

    private final NamedParameterJdbcTemplate jdbc;
    private final MetaModelService           metaModel;

    @Transactional
    public DeployResult deploy(MetaEntity entity) {
        String table = SqlIdentifiers.require(entity.getTableName());
        var added    = new ArrayList<String>();
        var warnings = new ArrayList<String>();

        boolean existed = tableExists(table);
        if (!existed) {
            String ddl = buildCreateTable(entity);
            log.info("Creating table '{}' for entity '{}'", table, entity.getName());
            log.debug("DDL: {}", ddl);
            jdbc.getJdbcTemplate().execute(ddl);
        } else {
            migrateColumns(entity, table, added, warnings);
        }
        ensureUniqueIndexes(entity, table);
        ensureForeignKeys(entity, table, warnings);

        metaModel.evictAll();
        return new DeployResult(entity.getName(), table,
            existed ? DeployResult.Action.MIGRATED : DeployResult.Action.CREATED,
            List.copyOf(added), List.copyOf(warnings));
    }

    /**
     * Deploys several entities: tables first, then a second pass so foreign
     * keys between entities resolve whatever the order of the list.
     */
    @Transactional
    public List<DeployResult> deployAll(List<MetaEntity> entities) {
        var results = new ArrayList<DeployResult>();
        for (MetaEntity e : entities) results.add(deploy(e));
        for (int i = 0; i < entities.size(); i++) {
            var warnings = new ArrayList<String>();
            ensureForeignKeys(entities.get(i), entities.get(i).getTableName(), warnings);
            DeployResult first = results.get(i);
            // Keep non-FK warnings from the first pass, FK warnings from the final pass.
            var merged = new ArrayList<>(first.warnings().stream()
                .filter(w -> !w.startsWith("Reference ")).toList());
            merged.addAll(warnings);
            results.set(i, new DeployResult(first.entityName(), first.tableName(),
                first.action(), first.addedColumns(), List.copyOf(merged)));
        }
        return results;
    }

    // ── DDL ───────────────────────────────────────────────────────────────

    String buildCreateTable(MetaEntity entity) {
        var cols = new ArrayList<String>();
        SYSTEM_COLUMN_DDL.forEach((name, ddl) -> cols.add(quote(name) + " " + ddl));
        for (MetaField field : entity.getFields()) {
            cols.add(quote(field.getName()) + " " + toSqlType(field));
        }
        return "CREATE TABLE " + quote(entity.getTableName()) +
               " (\n  " + String.join(",\n  ", cols) + "\n)";
    }

    private void migrateColumns(MetaEntity entity, String table,
                                List<String> added, List<String> warnings) {
        Map<String, String> existing = existingColumns(table);

        SYSTEM_COLUMN_DDL.forEach((name, ddl) -> {
            if (!existing.containsKey(name)) {
                // A NOT NULL system column added to a populated table needs its default.
                jdbc.getJdbcTemplate().execute(
                    "ALTER TABLE " + quote(table) + " ADD COLUMN " + quote(name) + " " + ddl);
                added.add(name);
            }
        });

        for (MetaField field : entity.getFields()) {
            String name = field.getName();
            String expected = toSqlType(field);
            if (!existing.containsKey(name)) {
                log.info("Adding column '{}' to '{}'", name, table);
                jdbc.getJdbcTemplate().execute(
                    "ALTER TABLE " + quote(table) + " ADD COLUMN " + quote(name) + " " + expected);
                added.add(name);
            } else if (!sameType(expected, existing.get(name))) {
                warnings.add("Column '" + name + "' is " + existing.get(name)
                    + " in the database but the model expects " + expected
                    + " — not altered automatically, migrate it manually");
            }
        }

        for (String column : existing.keySet()) {
            boolean known = SqlIdentifiers.isSystemColumn(column)
                || entity.getFields().stream().anyMatch(f -> f.getName().equals(column));
            if (!known) {
                warnings.add("Column '" + column + "' exists in the table but not in the model"
                    + " — kept (deploy never drops data)");
            }
        }
    }

    private void ensureUniqueIndexes(MetaEntity entity, String table) {
        for (MetaField field : entity.getFields()) {
            if (!field.isUnique()) continue;
            String index = indexName("uq", table, field.getName());
            jdbc.getJdbcTemplate().execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS " + quote(index) +
                " ON " + quote(table) + " (" + quote(field.getName()) + ")" +
                " WHERE \"deleted_at\" IS NULL");
        }
    }

    private void ensureForeignKeys(MetaEntity entity, String table, List<String> warnings) {
        for (MetaField field : entity.getFields()) {
            if (field.getType() != MetaFieldType.REFERENCE) continue;
            String target = field.getOptions() != null && field.getOptions().hasNonNull("targetEntity")
                ? field.getOptions().get("targetEntity").asText() : null;
            if (target == null || !SqlIdentifiers.isSafe(target)) {
                warnings.add("Reference '" + field.getName()
                    + "' has no valid options.targetEntity — no foreign key created");
                continue;
            }
            String targetTable = MetaEntity.defaultTableName(target);
            if (!tableExists(targetTable)) {
                warnings.add("Reference '" + field.getName() + "' targets '" + target
                    + "', which is not deployed yet — foreign key skipped, redeploy afterwards");
                continue;
            }
            String constraint = indexName("fk", table, field.getName());
            if (constraintExists(table, constraint)) continue;
            jdbc.getJdbcTemplate().execute(
                "ALTER TABLE " + quote(table) + " ADD CONSTRAINT " + quote(constraint) +
                " FOREIGN KEY (" + quote(field.getName()) + ")" +
                " REFERENCES " + quote(targetTable) + " (\"id\")");
        }
    }

    String toSqlType(MetaField field) {
        switch (field.getType()) {
            case STRING:     return "VARCHAR(" + intOption(field, "maxLength", 255, 1, 10_000) + ")";
            case TEXT:
            case RICH_TEXT:
            case FILE:
            case IMAGE:      return "TEXT";
            case EMAIL:
            case URL:
            case PHONE:      return "VARCHAR(255)";
            case INTEGER:    return "INTEGER";
            case LONG:       return "BIGINT";
            case DECIMAL: {
                int precision = intOption(field, "precision", 12, 1, 38);
                int scale     = intOption(field, "scale", 2, 0, precision);
                return "NUMERIC(" + precision + ", " + scale + ")";
            }
            case BOOLEAN:    return "BOOLEAN";
            case DATE:       return "DATE";
            case TIME:       return "TIME";
            case DATETIME:   return "TIMESTAMPTZ";
            case JSON:
            case MULTI_ENUM: return "JSONB";
            case ENUM:       return "VARCHAR(100)";
            case REFERENCE:  return "UUID";
            default: throw new IllegalStateException("Unsupported field type " + field.getType());
        }
    }

    /** Options are numbers read from JSON and clamped — never spliced in as text. */
    private int intOption(MetaField field, String key, int fallback, int min, int max) {
        int value = fallback;
        if (field.getOptions() != null && field.getOptions().has(key)) {
            value = field.getOptions().get(key).asInt(fallback);
        }
        return Math.max(min, Math.min(max, value));
    }

    // ── Catalog lookups ───────────────────────────────────────────────────

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = current_schema() AND table_name = :name
            """, new MapSqlParameterSource("name", table), Integer.class);
        return count != null && count > 0;
    }

    private boolean constraintExists(String table, String constraint) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE table_schema = current_schema() AND table_name = :table AND constraint_name = :name
            """, new MapSqlParameterSource("table", table).addValue("name", constraint), Integer.class);
        return count != null && count > 0;
    }

    /** Column name → normalised type description, e.g. {@code VARCHAR(255)}. */
    private Map<String, String> existingColumns(String table) {
        var out = new LinkedHashMap<String, String>();
        jdbc.query("""
            SELECT column_name, udt_name, character_maximum_length, numeric_precision, numeric_scale
            FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = :name
            ORDER BY ordinal_position
            """, new MapSqlParameterSource("name", table), rs -> {
                String udt = rs.getString("udt_name");
                String type;
                switch (udt) {
                    case "varchar":     type = "VARCHAR(" + rs.getInt("character_maximum_length") + ")"; break;
                    case "numeric":     type = "NUMERIC(" + rs.getInt("numeric_precision") + ", "
                                               + rs.getInt("numeric_scale") + ")"; break;
                    case "int4":        type = "INTEGER"; break;
                    case "int8":        type = "BIGINT"; break;
                    case "bool":        type = "BOOLEAN"; break;
                    case "timestamptz": type = "TIMESTAMPTZ"; break;
                    default:            type = udt.toUpperCase();
                }
                out.put(rs.getString("column_name"), type);
            });
        return out;
    }

    private boolean sameType(String expected, String actual) {
        return expected.replace(" ", "").equalsIgnoreCase(actual.replace(" ", ""));
    }

    /** PostgreSQL truncates identifiers at 63 characters; keep ours unique below that. */
    private String indexName(String prefix, String table, String column) {
        String name = prefix + "_" + table + "_" + column;
        if (name.length() <= 63) return name;
        return name.substring(0, 54) + "_" + Integer.toHexString(name.hashCode());
    }

    private static Map<String, String> systemColumns() {
        var m = new LinkedHashMap<String, String>();
        m.put("id",         "UUID PRIMARY KEY");
        m.put("state",      "VARCHAR(100)");
        m.put("created_at", "TIMESTAMPTZ NOT NULL DEFAULT NOW()");
        m.put("updated_at", "TIMESTAMPTZ NOT NULL DEFAULT NOW()");
        m.put("created_by", "VARCHAR(255)");
        m.put("updated_by", "VARCHAR(255)");
        m.put("deleted_at", "TIMESTAMPTZ");
        return m;
    }

    // ── Result ────────────────────────────────────────────────────────────

    public record DeployResult(
        String       entityName,
        String       tableName,
        Action       action,
        List<String> addedColumns,
        List<String> warnings
    ) {
        public enum Action { CREATED, MIGRATED }

        public String summary() {
            String base = action == Action.CREATED
                ? "Table '" + tableName + "' created"
                : addedColumns.isEmpty()
                    ? "Table '" + tableName + "' already up to date"
                    : "Table '" + tableName + "' migrated — added columns: " + addedColumns;
            return warnings.isEmpty() ? base : base + " (" + warnings.size() + " warning(s))";
        }
    }
}
