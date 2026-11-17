package io.metagen.engine.workflow;

import java.util.Map;
import java.util.UUID;

/** Outcome of a successful transition. */
public record TransitionResult(
    String              entityName,
    UUID                recordId,
    String              fromState,
    String              toState,
    String              transitionLabel,
    Map<String, Object> record
) {}
