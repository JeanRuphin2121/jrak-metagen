package io.metagen.engine.expr;

/** Thrown when a declarative condition is malformed or references unknown fields. */
public class ConditionException extends IllegalArgumentException {
    public ConditionException(String message) {
        super(message);
    }
}
