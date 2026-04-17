package io.metagen.core.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Central metadata object describing a business entity (table).
 *
 * <p>A {@code MetaEntity} is the equivalent of a database table definition
 * combined with its business-layer configuration. The runtime engine reads
 * this object to:</p>
 * <ul>
 *   <li>Dynamically expose a REST CRUD API at {@code /api/data/{name}}</li>
 *   <li>Generate forms and list views in the frontend</li>
 *   <li>Apply the associated workflow and RBAC rules</li>
 * </ul>
 *
 * <h2>Naming conventions</h2>
 * <ul>
 *   <li>{@code name} — technical identifier in snake_case (e.g. {@code supplier})</li>
 *   <li>{@code label} — human-readable display name (e.g. {@code Fournisseur})</li>
 *   <li>{@code tableName} — actual PostgreSQL table that stores the data
 *       (defaults to {@code data_<name>})</li>
 * </ul>
 *
 * <h2>Auditing</h2>
 * When {@code auditable = true} the engine automatically adds
 * {@code created_at}, {@code updated_at}, {@code created_by}, and
 * {@code updated_by} columns to the generated table.
 *
 * <h2>Soft delete</h2>
 * When {@code softDelete = true} DELETE operations set a {@code deleted_at}
 * timestamp instead of physically removing the row.
 */
@Entity
@Table(
    name = "meta_entity",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_meta_entity_name",       columnNames = "name"),
        @UniqueConstraint(name = "uq_meta_entity_table_name", columnNames = "table_name")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * Technical name in snake_case. Used as the URL path segment
     * and as the base for the generated table name.
     * Example: {@code supplier}, {@code purchase_order}.
     */
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z][a-z0-9_]*$",
             message = "name must be lowercase snake_case")
    @Column(nullable = false, length = 100)
    private String name;

    /** Human-readable display name shown in the UI. */
    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String label;

    /**
     * Actual PostgreSQL table name for data rows.
     * Defaults to {@code data_<name>} if not set explicitly.
     */
    @NotBlank
    @Size(max = 100)
    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    /** Optional description shown in the Studio. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Whether DELETE operations are soft (set deleted_at) or hard.
     * Defaults to {@code true}.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean softDelete = true;

    /**
     * Whether the engine should automatically track
     * created_at / updated_at / created_by / updated_by.
     * Defaults to {@code true}.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean auditable = true;

    /**
     * Fields (columns) belonging to this entity.
     * Ordered by {@link MetaField#position}.
     */
    @Builder.Default
    @OneToMany(
        mappedBy = "entity",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("position ASC")
    private List<MetaField> fields = new ArrayList<>();

    /**
     * Relations declared on this entity (source side).
     * Loaded lazily — use the repository to fetch with JOIN FETCH
     * when you need the full relation graph.
     */
    @Builder.Default
    @OneToMany(
        mappedBy = "sourceEntity",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private List<MetaRelation> relations = new ArrayList<>();

    /**
     * Optional workflow that drives the lifecycle of records.
     * When set, records have a {@code state} field managed by
     * the workflow engine.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id",
                foreignKey = @ForeignKey(name = "fk_entity_workflow"))
    private MetaWorkflow workflow;

    /** Custom views configured for this entity. */
    @Builder.Default
    @OneToMany(
        mappedBy = "entity",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private List<MetaView> views = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Helpers ──────────────────────────────────────────────────────

    /** Adds a field and wires the back-reference. */
    public void addField(MetaField field) {
        field.setEntity(this);
        this.fields.add(field);
    }

    /** Removes a field and clears the back-reference. */
    public void removeField(MetaField field) {
        this.fields.remove(field);
        field.setEntity(null);
    }

    /** Derived default table name following the {@code data_<name>} convention. */
    public static String defaultTableName(String entityName) {
        return "data_" + entityName;
    }
}
