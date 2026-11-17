package io.metagen.engine.rbac;

import java.util.Set;

/** What a given set of roles may do on one entity — sent to the UI to shape screens. */
public record EntityAccess(
    boolean     canRead,
    boolean     canWrite,
    boolean     canDelete,
    Set<String> hiddenFields,
    Set<String> readonlyFields
) {}
