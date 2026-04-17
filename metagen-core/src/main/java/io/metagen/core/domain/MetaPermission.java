package io.metagen.core.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.UUID;

/**
 * Grants a {@link MetaRole} specific access rights on a {@link MetaEntity}.
 *
 * <p>Permissions are checked by the runtime engine on every API call.
 * The engine intersects the user's roles against all matching
 * {@code MetaPermission} rows to decide whether to allow the operation.</p>
 *
 * <h2>Row-level filtering</h2>
 * <p>{@code rowFilterScript} is a Groovy snippet returning a SQL WHERE clause
 * fragment. It receives a {@code user} variable (the authenticated principal).
 * This allows data isolation without separate tenants.</p>
 * <pre>
 * // Only show records assigned to the current user
 * return "assigned_to = '" + user.id + "'"
 * </pre>
 *
 * <h2>Field masking</h2>
 * <p>{@code fieldMask} is a JSONB object that can hide or redact specific
 * fields for users of this role.</p>
 * <pre>
 * {
 *   "hidden":   ["salary", "ssn"],
 *   "readonly": ["status", "created_by"]
 * }
 * </pre>
 */
@Entity
@Table(
    name = "meta_permission",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_meta_permission_role_entity",
        columnNames = {"role_id", "entity_id"}
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Role that this permission entry applies to. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "role_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_permission_role")
    )
    private MetaRole role;

    /** Entity that this permission entry controls access to. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "entity_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_permission_entity")
    )
    private MetaEntity entity;

    /** Allow GET /api/data/{entity} and GET /api/data/{entity}/{id}. */
    @Builder.Default
    @Column(name = "can_read", nullable = false)
    private boolean canRead = false;

    /** Allow POST and PUT /api/data/{entity}. */
    @Builder.Default
    @Column(name = "can_write", nullable = false)
    private boolean canWrite = false;

    /** Allow DELETE /api/data/{entity}/{id}. */
    @Builder.Default
    @Column(name = "can_delete", nullable = false)
    private boolean canDelete = false;

    /**
     * Optional Groovy expression that returns a SQL WHERE clause fragment.
     * Applied automatically to every SELECT for this role + entity pair.
     * {@code null} means no row-level restriction.
     */
    @Column(name = "row_filter_script", columnDefinition = "TEXT")
    private String rowFilterScript;

    /**
     * Optional JSONB config for field-level masking.
     * Keys: {@code hidden} (array of field names to exclude from responses),
     *       {@code readonly} (array of field names that cannot be written).
     */
    @Type(JsonType.class)
    @Column(name = "field_mask_json", columnDefinition = "jsonb")
    private JsonNode fieldMask;
}
