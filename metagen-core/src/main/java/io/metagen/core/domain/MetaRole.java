package io.metagen.core.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A role in the RBAC (Role-Based Access Control) system.
 *
 * <p>Roles are assigned to users and govern what they can see and do.
 * Permissions are defined at the entity level via {@link MetaPermission}.</p>
 *
 * <h2>Built-in roles</h2>
 * Metagen seeds two built-in roles that cannot be deleted:
 * <ul>
 *   <li>{@code ADMIN} — full access to all entities and the Studio</li>
 *   <li>{@code USER}  — read access to all entities (override per entity)</li>
 * </ul>
 */
@Entity
@Table(
    name = "meta_role",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_meta_role_name",
        columnNames = "name"
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * Technical role name in UPPER_SNAKE_CASE.
     * Used in JWT claims and transition {@code requiredRole} fields.
     * Example: {@code BUYER}, {@code WAREHOUSE_MANAGER}.
     */
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
             message = "name must be UPPER_SNAKE_CASE")
    @Column(nullable = false, length = 100)
    private String name;

    /** Human-readable label shown in the Studio. */
    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Whether this role was created by the platform (not deletable).
     * Defaults to {@code false} — only the seeded ADMIN/USER roles are built-in.
     */
    @Builder.Default
    @Column(name = "built_in", nullable = false)
    private boolean builtIn = false;

    /** Permissions granted to users with this role. */
    @Builder.Default
    @OneToMany(
        mappedBy = "role",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private List<MetaPermission> permissions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
