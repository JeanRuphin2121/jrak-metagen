package io.metagen.core.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes a relationship between two {@link MetaEntity} instances.
 *
 * <p>Relations are declared on the source entity and referenced from the
 * target. The runtime engine uses this metadata to:</p>
 * <ul>
 *   <li>Generate the correct JOIN queries</li>
 *   <li>Render sub-lists or lookup fields in the UI</li>
 *   <li>Apply cascade delete if configured</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>
 * MetaRelation {
 *   sourceEntity = "customer",
 *   targetEntity = "order",
 *   type         = ONE_TO_MANY,
 *   fieldName    = "orders",
 *   label        = "Commandes"
 * }
 * </pre>
 * This generates a {@code /api/data/customer/{id}/orders} sub-resource
 * and displays an inline order list on the customer form.
 */
@Entity
@Table(name = "meta_relation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** The entity that owns (declares) this relation. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "source_entity_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_relation_source")
    )
    private MetaEntity sourceEntity;

    /** The entity pointed to by this relation. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "target_entity_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_relation_target")
    )
    private MetaEntity targetEntity;

    /** Cardinality of the relation. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetaRelationType type;

    /**
     * Technical field name used in API responses and URL paths.
     * Example: {@code orders}, {@code assigned_to}.
     */
    @NotBlank
    @Size(max = 100)
    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    /** Human-readable label for the relation in the UI. */
    @Size(max = 200)
    @Column(length = 200)
    private String label;

    /**
     * Whether deleting the source record should cascade-delete
     * the related target records.
     * Defaults to {@code false} — prefer explicit deletion.
     */
    @Builder.Default
    @Column(name = "cascade_delete", nullable = false)
    private boolean cascadeDelete = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
