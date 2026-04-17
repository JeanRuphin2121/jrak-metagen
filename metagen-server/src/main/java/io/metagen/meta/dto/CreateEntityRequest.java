package io.metagen.meta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for creating a new entity via the Studio API.
 *
 * @param name        snake_case technical name (e.g. {@code supplier})
 * @param label       human-readable label (e.g. {@code Fournisseur})
 * @param description optional description shown in the Studio
 * @param softDelete  whether DELETE is soft (default: true)
 * @param auditable   whether created_at/updated_at/... are auto-managed (default: true)
 * @param workflowId  optional UUID of an existing workflow to attach
 * @param fields      initial fields to create with the entity
 */
public record CreateEntityRequest(

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "name must be snake_case")
    String name,

    @NotBlank @Size(max = 200)
    String label,

    String description,

    boolean softDelete,
    boolean auditable,

    String workflowId,

    @Valid
    List<CreateFieldRequest> fields
) {
    /** Canonical defaults match entity domain defaults. */
    public CreateEntityRequest {
        if (fields == null) fields = List.of();
    }
}
