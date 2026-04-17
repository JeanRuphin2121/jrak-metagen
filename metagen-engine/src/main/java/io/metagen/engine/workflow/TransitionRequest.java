package io.metagen.engine.workflow;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Encapsulates all data needed to execute a workflow transition.
 *
 * @param entityName   technical entity name (e.g. {@code purchase_order})
 * @param recordId     id of the record to transition
 * @param transitionId id of the {@link io.metagen.core.domain.MetaTransition} to trigger
 * @param context      optional key-value data passed to condition/action scripts
 * @param userRoles    roles of the authenticated user (checked against requiredRole)
 * @param username     login name used for auditing
 */
public record TransitionRequest(
    String              entityName,
    UUID                recordId,
    UUID                transitionId,
    Map<String, Object> context,
    List<String>        userRoles,
    String              username
) {}
