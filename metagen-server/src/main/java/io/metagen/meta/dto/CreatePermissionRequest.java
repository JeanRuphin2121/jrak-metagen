package io.metagen.meta.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * @param rowFilter declarative filter limiting the visible rows, e.g.
 *                  {@code {"field":"created_by","op":"eq","value":"$user"}}
 * @param fieldMask {@code {"hidden":["salary"],"readonly":["status"]}}
 */
public record CreatePermissionRequest(
    @NotBlank String entityName,
    boolean      canRead,
    boolean      canWrite,
    boolean      canDelete,
    JsonNode     rowFilter,
    JsonNode     fieldMask
) {}
