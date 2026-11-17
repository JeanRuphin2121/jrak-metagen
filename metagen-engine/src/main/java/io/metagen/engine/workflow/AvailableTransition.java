package io.metagen.engine.workflow;

import java.util.UUID;

/** A transition the current user may trigger on a record right now. */
public record AvailableTransition(
    UUID   id,
    String label,
    String toState,
    String toStateLabel
) {}
