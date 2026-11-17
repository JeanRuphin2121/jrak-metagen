package io.metagen.meta.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.metagen.core.domain.MetaFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to add a field to an entity.
 *
 * <p>{@code searchable} defaults to {@code true} when omitted. {@code position}
 * is optional: a missing position appends the field at the end.
 */
public record CreateFieldRequest(
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "name must be snake_case")
    String name,

    @NotBlank @Size(max = 200)
    String label,

    @NotNull
    MetaFieldType type,

    boolean required,

    boolean unique,

    Boolean searchable,

    String   defaultValue,

    /** Type-specific settings: maxLength, precision/scale, values (ENUM), targetEntity (REFERENCE). */
    JsonNode options,

    Integer  position
) {
    public CreateFieldRequest {
        if (searchable == null) searchable = Boolean.TRUE;
    }
}
