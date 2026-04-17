package io.metagen.core.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

/**
 * A directed edge in a {@link MetaWorkflow} — allows moving a record
 * from one {@link MetaState} to another.
 *
 * <h2>Scripts</h2>
 * <p>Both {@code conditionScript} and {@code actionScript} are Groovy
 * snippets executed by the workflow engine. They receive a {@code record}
 * variable containing the current record's data as a {@code Map}.</p>
 *
 * <pre>
 * // conditionScript example: only allow if amount > 1000
 * return record.amount > 1000
 *
 * // actionScript example: send a notification
 * notificationService.notify(record.assignedTo, "Order approved")
 * </pre>
 *
 * <h2>Role restriction</h2>
 * <p>When {@code requiredRole} is set, only users with that role can
 * trigger this transition. The RBAC engine checks this before executing.</p>
 */
@Entity
@Table(name = "meta_transition")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaTransition {

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
        foreignKey = @ForeignKey(name = "fk_transition_workflow")
    )
    private MetaWorkflow workflow;

    /** State from which the transition originates. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "from_state_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_transition_from_state")
    )
    private MetaState fromState;

    /** State the record moves to when this transition is triggered. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "to_state_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_transition_to_state")
    )
    private MetaState toState;

    /** Action label shown on the button that triggers this transition. */
    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String label;

    /**
     * Optional Groovy script evaluated before the transition.
     * Must return {@code true} for the transition to proceed.
     * {@code null} means always allowed (subject to role check).
     */
    @Column(name = "condition_script", columnDefinition = "TEXT")
    private String conditionScript;

    /**
     * Optional Groovy script executed after the state change is persisted.
     * Use for side effects: notifications, computed field updates, etc.
     */
    @Column(name = "action_script", columnDefinition = "TEXT")
    private String actionScript;

    /**
     * Role name required to trigger this transition.
     * {@code null} means any authenticated user can trigger it.
     */
    @Size(max = 100)
    @Column(name = "required_role", length = 100)
    private String requiredRole;
}
