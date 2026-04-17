package io.metagen.meta.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for granting a role access to an entity.
 *
 * @param entityName      technical name of the entity
 * @param canRead         allow GET /api/data/{entity}
 * @param canWrite        allow POST + PUT
 * @param canDelete       allow DELETE
 * @param rowFilterScript Groovy snippet returning SQL WHERE fragment (nullable)
 * @param fieldMask       JSON {@code {"hidden":["salary"],"readonly":["status"]}} (nullable)
 */
public record CreatePermissionRequest(
    @NotBlank String  entityName,
    boolean           canRead,
    boolean           canWrite,
    boolean           canDelete,
    String            rowFilterScript,
    JsonNode          fieldMask
) {}
