package io.metagen.meta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request to create or update an entity definition.
 *
 * <p>{@code softDelete} and {@code auditable} default to {@code true} when
 * omitted (a primitive {@code boolean} would silently default to false).
 */
public record CreateEntityRequest(
    @NotBlank
    @Size(max = 58)
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "name must be snake_case")
    String name,

    @NotBlank @Size(max = 200)
    String label,

    String description,

    Boolean softDelete,

    Boolean auditable,

    /** Optional workflow name to attach at creation. */
    String workflow,

    @Valid
    List<CreateFieldRequest> fields
) {
    public CreateEntityRequest {
        if (softDelete == null) softDelete = Boolean.TRUE;
        if (auditable == null)  auditable  = Boolean.TRUE;
        if (fields == null)     fields     = List.of();
    }
}
