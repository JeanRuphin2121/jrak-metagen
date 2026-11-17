package io.metagen.core.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines the lifecycle of a {@link MetaEntity}'s records.
 *
 * <p>A workflow is a finite state machine composed of {@link MetaState states}
 * and {@link MetaTransition transitions}. When an entity has a workflow, each
 * record carries a {@code state} field. The workflow engine enforces valid
 * transitions and can execute scripts on state changes.</p>
 *
 * <h2>Example — Purchase Order workflow</h2>
 * <pre>
 * States:     DRAFT → SUBMITTED → APPROVED → RECEIVED | REJECTED
 * Transitions:
 *   DRAFT      → SUBMITTED  (label: "Soumettre",  role: BUYER)
 *   SUBMITTED  → APPROVED   (label: "Approuver",  role: MANAGER)
 *   SUBMITTED  → REJECTED   (label: "Rejeter",    role: MANAGER)
 *   APPROVED   → RECEIVED   (label: "Réceptionner", role: WAREHOUSE)
 * </pre>
 */
@Entity
@Table(
    name = "meta_workflow",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_meta_workflow_name",
        columnNames = "name"
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * Technical name in snake_case.
     * Example: {@code purchase_order_lifecycle}.
     */
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z][a-z0-9_]*$",
             message = "name must be lowercase snake_case")
    @Column(nullable = false, length = 100)
    private String name;

    /** Human-readable name shown in the Studio. */
    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * States that compose this workflow.
     * Exactly one state must have {@code initial = true}.
     */
    @Builder.Default
    @OneToMany(
        mappedBy = "workflow",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("position ASC")
    private Set<MetaState> states = new LinkedHashSet<>();

    /**
     * Transitions between states.
     * Each transition belongs to this workflow and references
     * two states within it.
     */
    @Builder.Default
    @OneToMany(
        mappedBy = "workflow",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private Set<MetaTransition> transitions = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    // ── Helpers ──────────────────────────────────────────────────────

    /** Returns the initial state, or empty if the workflow is misconfigured. */
    public Optional<MetaState> getInitialState() {
        return states.stream().filter(MetaState::isInitial).findFirst();
    }

    /** Adds a state and wires the back-reference. */
    public void addState(MetaState state) {
        state.setWorkflow(this);
        this.states.add(state);
    }

    /** Adds a transition and wires the back-reference. */
    public void addTransition(MetaTransition transition) {
        transition.setWorkflow(this);
        this.transitions.add(transition);
    }
}
