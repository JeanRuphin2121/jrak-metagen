package io.metagen.meta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a role.
 *
 * @param name  UPPER_SNAKE_CASE technical name (e.g. {@code WAREHOUSE_MANAGER})
 * @param label human-readable label
 */
public record CreateRoleRequest(
    @NotBlank @Size(max = 100)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "name must be UPPER_SNAKE_CASE")
    String name,

    @NotBlank @Size(max = 200)
    String label,

    String description
) {}
