package io.metagen.meta.dto;

import io.metagen.core.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API response representing a {@link MetaEntity} with its fields.
 * Used by both the list endpoint (without fields) and the detail endpoint (with fields).
 */
public record MetaEntityResponse(
    UUID          id,
    String        name,
    String        label,
    String        tableName,
    String        description,
    boolean       softDelete,
    boolean       auditable,
    String        workflowName,
    List<FieldDto> fields,
    Instant       createdAt,
    Instant       updatedAt
) {

    public record FieldDto(
        UUID          id,
        String        name,
        String        label,
        MetaFieldType type,
        boolean       required,
        boolean       unique,
        boolean       searchable,
        String        defaultValue,
        int           position
    ) {}

    // ── Factory ──────────────────────────────────────────────────────

    public static MetaEntityResponse from(MetaEntity e) {
        var fields = e.getFields().stream()
            .map(f -> new FieldDto(
                f.getId(), f.getName(), f.getLabel(), f.getType(),
                f.isRequired(), f.isUnique(), f.isSearchable(),
                f.getDefaultValue(), f.getPosition()))
            .toList();

        return new MetaEntityResponse(
            e.getId(),
            e.getName(),
            e.getLabel(),
            e.getTableName(),
            e.getDescription(),
            e.isSoftDelete(),
            e.isAuditable(),
            e.getWorkflow() != null ? e.getWorkflow().getName() : null,
            fields,
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}
