package io.metagen.engine.workflow;

import io.metagen.core.domain.MetaEntity;

import java.util.Map;
import java.util.UUID;

/** Everything a {@link WorkflowAction} needs to know about the transition that just happened. */
public record TransitionContext(
    MetaEntity          entity,
    UUID                recordId,
    Map<String, Object> record,
    String              fromState,
    String              toState,
    String              transitionLabel,
    String              username,
    String              comment
) {}
