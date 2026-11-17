package io.metagen.engine.workflow;

import java.util.List;
import java.util.UUID;

/** A user's request to move one record through a workflow transition. */
public record TransitionRequest(
    String       entityName,
    UUID         recordId,
    UUID         transitionId,
    String       comment,
    List<String> userRoles,
    String       username
) {}
