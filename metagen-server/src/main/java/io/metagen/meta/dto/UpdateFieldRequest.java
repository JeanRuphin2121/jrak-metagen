package io.metagen.meta.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Editable properties of an existing field. Name and type are immutable. */
public record UpdateFieldRequest(
    @NotBlank @Size(max = 200)
    String label,
    boolean required,
    boolean unique,
    Boolean searchable,
    String defaultValue,
    JsonNode options,
    Integer position
) {
    public UpdateFieldRequest {
        if (searchable == null) searchable = Boolean.TRUE;
    }
}
