package io.metagen.engine.expr;

/**
 * Tells a {@link Condition} which fields exist and how to convert a literal
 * into the Java type of the underlying column.
 */
public interface FieldResolver {

    boolean exists(String field);

    /**
     * Converts a raw literal (from JSON) to the column's Java type.
     *
     * @throws IllegalArgumentException if the literal cannot be converted
     */
    Object coerce(String field, Object raw);
}
