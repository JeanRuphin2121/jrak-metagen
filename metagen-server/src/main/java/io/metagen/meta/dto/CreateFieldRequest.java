package io.metagen.meta.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.metagen.core.domain.MetaFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for adding or updating a field on an entity.
 *
 * @param name         snake_case technical name (e.g. {@code first_name})
 * @param label        human-readable label (e.g. {@code Prénom})
 * @param type         field data type
 * @param required     whether the field is mandatory on save
 * @param unique       whether a UNIQUE constraint should be added
 * @param searchable   whether the field is included in free-text search
 * @param defaultValue default value expression
 * @param options      type-specific JSON config (enum values, precision, reference, ...)
 * @param position     display order (0-based)
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
    boolean searchable,

    String   defaultValue,
    JsonNode options,
    int      position
) {}
