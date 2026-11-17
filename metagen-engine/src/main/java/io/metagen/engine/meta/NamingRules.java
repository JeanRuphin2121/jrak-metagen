package io.metagen.engine.meta;

import io.metagen.engine.sql.SqlIdentifiers;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Naming rules for metadata, enforced wherever metadata can enter the system
 * (Studio API, YAML import, AI proposals) so no path can bypass them.
 */
public final class NamingRules {

    /** "data_" prefix + name must fit PostgreSQL's 63-character identifier limit. */
    public static final int MAX_ENTITY_NAME = 58;

    private static final Pattern STATE = Pattern.compile("^[A-Z][A-Z0-9_]{0,99}$");
    private static final Pattern ROLE  = Pattern.compile("^[A-Z][A-Z0-9_]{0,99}$");

    private NamingRules() {}

    public static Optional<String> entityName(String name) {
        if (!SqlIdentifiers.isSafe(name)) {
            return Optional.of("entity name '" + name + "' must be snake_case (a-z, 0-9, _), starting with a letter");
        }
        if (name.length() > MAX_ENTITY_NAME) {
            return Optional.of("entity name '" + name + "' is longer than " + MAX_ENTITY_NAME + " characters");
        }
        return Optional.empty();
    }

    public static Optional<String> fieldName(String name) {
        if (!SqlIdentifiers.isSafe(name)) {
            return Optional.of("field name '" + name + "' must be snake_case (a-z, 0-9, _), starting with a letter");
        }
        if (SqlIdentifiers.isSystemColumn(name)) {
            return Optional.of("field name '" + name + "' is reserved by the engine ("
                + String.join(", ", SqlIdentifiers.SYSTEM_COLUMNS) + ")");
        }
        return Optional.empty();
    }

    public static Optional<String> workflowName(String name) {
        return SqlIdentifiers.isSafe(name)
            ? Optional.empty()
            : Optional.of("workflow name '" + name + "' must be snake_case");
    }

    public static Optional<String> stateName(String name) {
        return name != null && STATE.matcher(name).matches()
            ? Optional.empty()
            : Optional.of("state name '" + name + "' must be UPPER_SNAKE_CASE");
    }

    public static Optional<String> roleName(String name) {
        return name != null && ROLE.matcher(name).matches()
            ? Optional.empty()
            : Optional.of("role name '" + name + "' must be UPPER_SNAKE_CASE");
    }

    public static Optional<String> viewName(String name) {
        return SqlIdentifiers.isSafe(name)
            ? Optional.empty()
            : Optional.of("view name '" + name + "' must be snake_case");
    }
}
