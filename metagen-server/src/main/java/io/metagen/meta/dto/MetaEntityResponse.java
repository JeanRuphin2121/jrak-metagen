package io.metagen.meta.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.core.domain.MetaFieldType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MetaEntityResponse(
    UUID           id,
    String         name,
    String         label,
    String         tableName,
    String         description,
    boolean        softDelete,
    boolean        auditable,
    String         workflowName,
    List<FieldDto> fields,
    Instant        createdAt,
    Instant        updatedAt
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
        JsonNode      options,
        int           position
    ) {
        public static FieldDto from(MetaField f) {
            return new FieldDto(f.getId(), f.getName(), f.getLabel(), f.getType(),
                f.isRequired(), f.isUnique(), f.isSearchable(),
                f.getDefaultValue(), f.getOptions(), f.getPosition());
        }
    }

    public static MetaEntityResponse from(MetaEntity e) {
        return new MetaEntityResponse(
            e.getId(), e.getName(), e.getLabel(), e.getTableName(), e.getDescription(),
            e.isSoftDelete(), e.isAuditable(),
            e.getWorkflow() != null ? e.getWorkflow().getName() : null,
            e.getFields().stream().map(FieldDto::from).toList(),
            e.getCreatedAt(), e.getUpdatedAt());
    }
}
