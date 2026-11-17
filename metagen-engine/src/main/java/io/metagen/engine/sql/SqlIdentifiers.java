package io.metagen.engine.sql;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Single choke point for every identifier that ends up in dynamic SQL.
 *
 * <p>Values always travel as bind parameters; identifiers (table and column
 * names) cannot, so they are validated against a strict snake_case pattern and
 * double-quoted. Nothing that fails {@link #require(String)} can reach the
 * database, whatever path the metadata took (REST, YAML import, AI proposal).
 */
public final class SqlIdentifiers {

    private static final Pattern SAFE = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    /** Columns owned by the engine and present on every {@code data_*} table. */
    public static final List<String> SYSTEM_COLUMNS = List.of(
        "id", "state", "created_at", "updated_at", "created_by", "updated_by", "deleted_at");

    private static final Set<String> SYSTEM_SET = Set.copyOf(SYSTEM_COLUMNS);

    private SqlIdentifiers() {}

    public static boolean isSafe(String identifier) {
        return identifier != null && SAFE.matcher(identifier).matches();
    }

    /** Returns the identifier unchanged if it is safe, otherwise throws. */
    public static String require(String identifier) {
        if (!isSafe(identifier)) {
            throw new IllegalArgumentException(
                "Invalid identifier '" + identifier + "': expected snake_case, max 63 characters");
        }
        return identifier;
    }

    /** Validates then double-quotes an identifier, so reserved words (order, user…) are usable. */
    public static String quote(String identifier) {
        return "\"" + require(identifier) + "\"";
    }

    public static boolean isSystemColumn(String name) {
        return SYSTEM_SET.contains(name);
    }
}
