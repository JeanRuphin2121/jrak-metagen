package io.metagen.engine.workflow;

/**
 * Side effect attached to a workflow transition (send a notification, create a
 * related record, call another system…).
 *
 * <p>This is how business logic is plugged into a workflow: a named Spring bean
 * written in plain Java, referenced from metadata by {@link #name()}. It lives
 * in Git, is covered by the compiler and unit-tested with JUnit — nothing
 * executable is ever stored in the configuration database.
 *
 * <p>The action runs inside the transition's database transaction: throwing an
 * exception cancels the state change.
 */
public interface WorkflowAction {

    /** Unique snake_case name used in metadata ({@code action: notify_manager}). */
    String name();

    void execute(TransitionContext ctx);
}
