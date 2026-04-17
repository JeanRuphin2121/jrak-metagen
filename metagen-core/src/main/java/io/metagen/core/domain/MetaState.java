package io.metagen.core.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

/**
 * A single state in a {@link MetaWorkflow}.
 *
 * <p>States represent the lifecycle stages of an entity record
 * (e.g. DRAFT, SUBMITTED, APPROVED). The UI uses {@code color} and
 * {@code label} to render a status badge on list views and forms.</p>
 *
 * <h2>Constraints</h2>
 * <ul>
 *   <li>Each workflow must have exactly one state with {@code initial = true}.</li>
 *   <li>Final states ({@code final = true}) cannot be the source of a transition.</li>
 * </ul>
 */
@Entity
@Table(
    name = "meta_state",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_meta_state_workflow_name",
        columnNames = {"workflow_id", "name"}
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Parent workflow. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "workflow_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_state_workflow")
    )
    private MetaWorkflow workflow;

    /**
     * Technical state identifier in UPPER_SNAKE_CASE.
     * Example: {@code DRAFT}, {@code PENDING_APPROVAL}.
     */
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
             message = "name must be UPPER_SNAKE_CASE")
    @Column(nullable = false, length = 100)
    private String name;

    /** Human-readable label for the state badge. */
    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String label;

    /**
     * Whether this is the starting state for new records.
     * Exactly one state per workflow must be initial.
     */
    @Builder.Default
    @Column(name = "is_initial", nullable = false)
    private boolean initial = false;

    /**
     * Whether records in this state are considered closed/terminal.
     * No outgoing transitions are allowed from a final state.
     */
    @Builder.Default
    @Column(name = "is_final", nullable = false)
    private boolean terminal = false;

    /**
     * CSS color token or hex code used to render the status badge.
     * Examples: {@code green}, {@code #F59E0B}, {@code red}.
     */
    @Size(max = 20)
    @Column(length = 20)
    private String color;

    /**
     * Display order within the workflow diagram / kanban board.
     * 0-based.
     */
    @Builder.Default
    @Column(nullable = false)
    private int position = 0;
}
