package io.metagen.engine.workflow;

import java.util.Map;
import java.util.UUID;

/**
 * Result of a successfully executed workflow transition.
 *
 * @param entityName    entity that was transitioned
 * @param recordId      id of the record
 * @param fromState     state name before the transition
 * @param toState       state name after the transition
 * @param transitionLabel display label of the executed transition
 * @param record        updated record data after the state change
 */
public record TransitionResult(
    String              entityName,
    UUID                recordId,
    String              fromState,
    String              toState,
    String              transitionLabel,
    Map<String, Object> record
) {}
