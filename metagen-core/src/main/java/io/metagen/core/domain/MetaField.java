package io.metagen.core.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes a single column / attribute of a {@link MetaEntity}.
 *
 * <p>The {@code type} field drives both the PostgreSQL column definition and
 * the React input component rendered by the frontend. Additional type-specific
 * configuration (e.g. enum options, decimal precision, reference target) is
 * stored in the {@code options} JSONB field.</p>
 *
 * <h2>options JSON structure by type</h2>
 * <pre>
 * ENUM / MULTI_ENUM:
 *   { "values": ["DRAFT", "ACTIVE", "CLOSED"],
 *     "labels": {"DRAFT": "Brouillon", "ACTIVE": "Actif"} }
 *
 * DECIMAL:
 *   { "precision": 10, "scale": 2 }
 *
 * REFERENCE:
 *   { "targetEntity": "supplier", "displayField": "name" }
 *
 * STRING (with max length override):
 *   { "maxLength": 500 }
 * </pre>
 */
@Entity
@Table(
    name = "meta_field",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_meta_field_entity_name",
        columnNames = {"entity_id", "name"}
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Owning entity. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "entity_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_field_entity")
    )
    private MetaEntity entity;

    /**
     * Technical column name in snake_case.
     * Example: {@code first_name}, {@code invoice_date}.
     */
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z][a-z0-9_]*$",
             message = "name must be lowercase snake_case")
    @Column(nullable = false, length = 100)
    private String name;

    /** Human-readable label shown on forms and list headers. */
    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String label;

    /** Data type — drives SQL type and UI component. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MetaFieldType type;

    /** Whether this field is mandatory on create/update. */
    @Builder.Default
    @Column(nullable = false)
    private boolean required = false;

    /** Whether the engine should add a UNIQUE constraint on this column. */
    @Builder.Default
    @Column(name = "unique_constraint", nullable = false)
    private boolean unique = false;

    /**
     * Whether this field is included in full-text search indexes
     * and the default list view filter bar.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean searchable = true;

    /**
     * Default value expression.
     * Supports literal values and special tokens:
     * {@code NOW()} for timestamps, {@code CURRENT_USER} for user references.
     */
    @Column(name = "default_value")
    private String defaultValue;

    /**
     * Type-specific configuration stored as JSONB.
     * See class-level Javadoc for the expected structure per type.
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode options;

    /**
     * Display order within the entity (0-based).
     * Used to sort fields in forms and list columns.
     */
    @Builder.Default
    @Column(nullable = false)
    private int position = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
