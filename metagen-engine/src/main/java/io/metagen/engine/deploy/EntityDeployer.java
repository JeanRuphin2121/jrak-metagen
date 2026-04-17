package io.metagen.engine.deploy;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.core.domain.MetaFieldType;
import io.metagen.core.domain.MetaRelation;
import io.metagen.core.domain.MetaRelationType;
import io.metagen.engine.crud.DynamicCrudService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates or migrates the {@code data_*} table for a {@link MetaEntity}.
 *
 * <p>Calling {@link #deploy(MetaEntity)} is idempotent:</p>
 * <ul>
 *   <li>If the table does not exist → {@code CREATE TABLE}</li>
 *   <li>If the table exists → {@code ALTER TABLE ADD COLUMN} for any new fields</li>
 *   <li>Columns are <strong>never removed</strong> — safe, non-destructive migration</li>
 * </ul>
 *
 * <h2>Generated table structure</h2>
 * <pre>
 *   CREATE TABLE data_supplier (
 *     id          UUID        PRIMARY KEY,
 *     state       VARCHAR(100),            -- workflow state (if workflow attached)
 *     name        VARCHAR(255) NOT NULL,   -- from MetaField
 *     siret       VARCHAR(255) UNIQUE,
 *     created_at  TIMESTAMPTZ,             -- system column (if auditable)
 *     updated_at  TIMESTAMPTZ,
 *     created_by  VARCHAR(255),
 *     updated_by  VARCHAR(255),
 *     deleted_at  TIMESTAMPTZ              -- soft delete marker (if softDelete)
 *   );
 * </pre>
 *
 * <h2>Why not Flyway?</h2>
 * <p>Flyway manages the {@code meta_*} schema (known at build time).
 * {@code data_*} tables are created at runtime from user-defined metadata —
 * we cannot write Flyway migrations for schemas we don't know in advance.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDeployer {

    private final NamedParameterJdbcTemplate jdbc;
    private final DynamicCrudService         crudService;

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Deploys (creates or migrates) the data table for the given entity.
     *
     * @param entity entity with fields fully loaded
     * @return a human-readable summary of what was done
     */
    @Transactional
    public DeployResult deploy(MetaEntity entity) {
        String table = entity.getTableName();

        if (tableExists(table)) {
            return migrate(entity);
        } else {
            return create(entity);
        }
    }

    // ── CREATE ───────────────────────────────────────────────────────

    private DeployResult create(MetaEntity entity) {
        String sql = buildCreateTable(entity);
        log.info("Creating table '{}' for entity '{}'", entity.getTableName(), entity.getName());
        log.debug("DDL: {}", sql);
        jdbc.getJdbcTemplate().execute(sql);

        // Create foreign key constraints for REFERENCE fields
        createForeignKeys(entity);

        // Invalidate metadata cache
        crudService.evictCache(entity.getName());

        return new DeployResult(entity.getName(), entity.getTableName(),
                                DeployResult.Action.CREATED, List.of());
    }

    // ── MIGRATE ──────────────────────────────────────────────────────

    private DeployResult migrate(MetaEntity entity) {
        Set<String> existing = existingColumns(entity.getTableName());
        var added = new ArrayList<String>();

        for (MetaField field : entity.getFields()) {
            if (!existing.contains(field.getName())) {
                String alterSql = "ALTER TABLE " + entity.getTableName() +
                                  " ADD COLUMN IF NOT EXISTS " +
                                  fieldColumnDef(field);
                log.info("Adding column '{}' to '{}'", field.getName(), entity.getTableName());
                jdbc.getJdbcTemplate().execute(alterSql);
                added.add(field.getName());
            }
        }

        crudService.evictCache(entity.getName());
        return new DeployResult(entity.getName(), entity.getTableName(),
                                DeployResult.Action.MIGRATED, added);
    }

    // ── DDL builders ─────────────────────────────────────────────────

    private String buildCreateTable(MetaEntity entity) {
        var cols = new ArrayList<String>();

        // Primary key
        cols.add("id UUID PRIMARY KEY DEFAULT gen_random_uuid()");

        // Workflow state column
        if (entity.getWorkflow() != null) {
            cols.add("state VARCHAR(100)");
        }

        // User-defined fields
        for (MetaField field : entity.getFields()) {
            cols.add(fieldColumnDef(field));
        }

        // Auditing columns
        if (entity.isAuditable()) {
            cols.add("created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()");
            cols.add("updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()");
            cols.add("created_by VARCHAR(255)");
            cols.add("updated_by VARCHAR(255)");
        }

        // Soft delete marker
        if (entity.isSoftDelete()) {
            cols.add("deleted_at TIMESTAMPTZ");
        }

        return "CREATE TABLE " + entity.getTableName() +
               " (\n  " + String.join(",\n  ", cols) + "\n)";
    }

    /**
     * Builds the column definition fragment for a single field.
     * Example: {@code "name VARCHAR(255) NOT NULL"}
     */
    String fieldColumnDef(MetaField field) {
        var sb = new StringBuilder();
        sb.append(field.getName()).append(" ").append(toSqlType(field));

        if (field.isRequired()) {
            sb.append(" NOT NULL");
        }
        if (field.isUnique()) {
            sb.append(" UNIQUE");
        }
        if (field.getDefaultValue() != null) {
            sb.append(" DEFAULT '").append(field.getDefaultValue()).append("'");
        }
        return sb.toString();
    }

    /**
     * Maps a {@link MetaFieldType} to a PostgreSQL column type.
     * Decimal precision/scale and max string length are read from
     * {@link MetaField#getOptions()} when present.
     */
    String toSqlType(MetaField field) {
        return switch (field.getType()) {
            case STRING                   -> maxLength(field, 255);
            case TEXT, RICH_TEXT          -> "TEXT";
            case EMAIL, URL, PHONE        -> "VARCHAR(255)";
            case INTEGER                  -> "INTEGER";
            case LONG                     -> "BIGINT";
            case DECIMAL                  -> decimalType(field);
            case BOOLEAN                  -> "BOOLEAN";
            case DATE                     -> "DATE";
            case TIME                     -> "TIME";
            case DATETIME                 -> "TIMESTAMPTZ";
            case FILE, IMAGE              -> "TEXT";   // path or URL
            case JSON                     -> "JSONB";
            case ENUM                     -> "VARCHAR(100)";
            case MULTI_ENUM               -> "JSONB";
            case REFERENCE                -> "UUID";   // FK added separately
        };
    }

    private String maxLength(MetaField field, int defaultMax) {
        if (field.getOptions() != null && field.getOptions().has("maxLength")) {
            return "VARCHAR(" + field.getOptions().get("maxLength").asInt(defaultMax) + ")";
        }
        return "VARCHAR(" + defaultMax + ")";
    }

    private String decimalType(MetaField field) {
        int precision = 10, scale = 2;
        if (field.getOptions() != null) {
            if (field.getOptions().has("precision")) precision = field.getOptions().get("precision").asInt(10);
            if (field.getOptions().has("scale"))     scale     = field.getOptions().get("scale").asInt(2);
        }
        return "NUMERIC(" + precision + ", " + scale + ")";
    }

    /** Creates FK constraints for REFERENCE fields after the table exists. */
    private void createForeignKeys(MetaEntity entity) {
        for (MetaRelation rel : entity.getRelations()) {
            if (rel.getType() == MetaRelationType.MANY_TO_ONE ||
                rel.getType() == MetaRelationType.ONE_TO_ONE) {
                String fkSql = "ALTER TABLE " + entity.getTableName() +
                               " ADD CONSTRAINT fk_" + entity.getName() + "_" + rel.getFieldName() +
                               " FOREIGN KEY (" + rel.getFieldName() + "_id)" +
                               " REFERENCES " + rel.getTargetEntity().getTableName() + "(id)";
                try {
                    jdbc.getJdbcTemplate().execute(fkSql);
                } catch (Exception e) {
                    log.warn("Could not create FK for relation '{}': {}", rel.getFieldName(), e.getMessage());
                }
            }
        }
    }

    // ── Introspection helpers ─────────────────────────────────────────

    private boolean tableExists(String tableName) {
        String sql = """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = :name
            """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource("name", tableName), Integer.class);
        return count != null && count > 0;
    }

    private Set<String> existingColumns(String tableName) {
        String sql = """
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = :name
            """;
        return jdbc.queryForList(sql, new MapSqlParameterSource("name", tableName), String.class)
                   .stream().collect(Collectors.toSet());
    }

    // ── Result DTO ───────────────────────────────────────────────────

    public record DeployResult(
        String       entityName,
        String       tableName,
        Action       action,
        List<String> addedColumns
    ) {
        public enum Action { CREATED, MIGRATED }

        public String summary() {
            return action == Action.CREATED
                ? "Table '" + tableName + "' created for entity '" + entityName + "'"
                : "Table '" + tableName + "' migrated — added columns: " + addedColumns;
        }
    }
}
