package io.metagen.engine.crud;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.core.domain.MetaFieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DynamicQueryBuilder}.
 *
 * No Spring context — pure logic tests. Tests verify:
 *  - correct SQL structure for SELECT / INSERT / UPDATE / DELETE
 *  - sort-field injection prevention (unknown field falls back to created_at)
 *  - field name validation rejects non-declared columns
 *  - search clause is built only for searchable text fields
 */
class DynamicQueryBuilderTest {

    private DynamicQueryBuilder builder;
    private MetaEntity          entity;

    @BeforeEach
    void setUp() {
        builder = new DynamicQueryBuilder();

        MetaField nameField = MetaField.builder()
            .name("name").label("Name")
            .type(MetaFieldType.STRING)
            .required(true).searchable(true).position(0)
            .build();

        MetaField ageField = MetaField.builder()
            .name("age").label("Age")
            .type(MetaFieldType.INTEGER)
            .required(false).searchable(false).position(1)
            .build();

        entity = MetaEntity.builder()
            .name("person").label("Person")
            .tableName("data_person")
            .softDelete(true).auditable(true)
            .build();
        entity.getFields().add(nameField);
        entity.getFields().add(ageField);
    }

    // ── buildSelect ───────────────────────────────────────────────────

    @Test
    @DisplayName("buildSelect includes mandatory system columns")
    void buildSelect_containsSystemColumns() {
        String sql = builder.buildSelect(entity, null, null, null, "ASC");
        assertThat(sql).contains("id", "created_at", "deleted_at");
    }

    @Test
    @DisplayName("buildSelect appends soft-delete filter when softDelete=true")
    void buildSelect_softDeleteFilter() {
        String sql = builder.buildSelect(entity, null, null, null, "ASC");
        assertThat(sql).contains("deleted_at IS NULL");
    }

    @Test
    @DisplayName("buildSelect appends row filter when provided")
    void buildSelect_rowFilter() {
        String sql = builder.buildSelect(entity, "tenant_id = :tid", null, null, "ASC");
        assertThat(sql).contains("AND (tenant_id = :tid)");
    }

    @Test
    @DisplayName("buildSelect adds ILIKE clause only for searchable STRING fields")
    void buildSelect_searchClause() {
        String sql = builder.buildSelect(entity, null, "alice", null, "ASC");
        assertThat(sql).contains("name ILIKE :search");
        assertThat(sql).doesNotContain("age ILIKE");
    }

    @Test
    @DisplayName("buildSelect falls back to created_at for unknown sort field")
    void buildSelect_sortFallback() {
        String sql = builder.buildSelect(entity, null, null, "drop_table", "ASC");
        assertThat(sql).contains("ORDER BY created_at");
        assertThat(sql).doesNotContain("drop_table");
    }

    @Test
    @DisplayName("buildSelect uses declared field as sort column")
    void buildSelect_validSortField() {
        String sql = builder.buildSelect(entity, null, null, "name", "DESC");
        assertThat(sql).contains("ORDER BY name DESC");
    }

    @Test
    @DisplayName("buildSelect uses LIMIT / OFFSET named params")
    void buildSelect_pagination() {
        String sql = builder.buildSelect(entity, null, null, null, "ASC");
        assertThat(sql).contains(":pageSize").contains(":offset");
    }

    // ── buildCount ────────────────────────────────────────────────────

    @Test
    @DisplayName("buildCount uses COUNT(*) and same WHERE conditions")
    void buildCount_structure() {
        String sql = builder.buildCount(entity, null, "bob");
        assertThat(sql).startsWith("SELECT COUNT(*)");
        assertThat(sql).contains("ILIKE :search");
        assertThat(sql).doesNotContain(":pageSize");
    }

    // ── buildInsert ───────────────────────────────────────────────────

    @Test
    @DisplayName("buildInsert includes RETURNING and auditing columns")
    void buildInsert_structure() {
        String sql = builder.buildInsert(entity, List.of("name", "age"));
        assertThat(sql).startsWith("INSERT INTO data_person");
        assertThat(sql).contains(":name", ":age", ":id", "created_at", "RETURNING");
    }

    @Test
    @DisplayName("buildInsert rejects undeclared field name")
    void buildInsert_rejectsUnknownField() {
        assertThatThrownBy(() -> builder.buildInsert(entity, List.of("unknown_col")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown_col");
    }

    // ── buildUpdate ───────────────────────────────────────────────────

    @Test
    @DisplayName("buildUpdate generates SET clause and RETURNING")
    void buildUpdate_structure() {
        String sql = builder.buildUpdate(entity, List.of("name"));
        assertThat(sql).contains("SET name = :name", "WHERE id = :id", "RETURNING");
    }

    // ── buildSoftDelete ───────────────────────────────────────────────

    @Test
    @DisplayName("buildSoftDelete sets deleted_at without removing the row")
    void buildSoftDelete_structure() {
        String sql = builder.buildSoftDelete(entity);
        assertThat(sql).contains("SET deleted_at = NOW()");
        assertThat(sql).doesNotContain("DELETE FROM");
    }

    // ── buildHardDelete ───────────────────────────────────────────────

    @Test
    @DisplayName("buildHardDelete uses DELETE FROM")
    void buildHardDelete_structure() {
        String sql = builder.buildHardDelete(entity);
        assertThat(sql).startsWith("DELETE FROM data_person WHERE id = :id");
    }

    // ── validateFieldName ─────────────────────────────────────────────

    @Test
    @DisplayName("validateFieldName accepts declared fields")
    void validateFieldName_accepts() {
        assertThatNoException()
            .isThrownBy(() -> builder.validateFieldName("name", entity));
    }

    @Test
    @DisplayName("validateFieldName rejects undeclared names")
    void validateFieldName_rejects() {
        assertThatThrownBy(() -> builder.validateFieldName("'; DROP TABLE users; --", entity))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── columnList ────────────────────────────────────────────────────

    @Test
    @DisplayName("columnList always includes id + declared fields + audit columns")
    void columnList_contents() {
        String cols = builder.columnList(entity);
        assertThat(cols).contains("id", "name", "age", "created_at", "updated_at", "deleted_at");
    }
}
