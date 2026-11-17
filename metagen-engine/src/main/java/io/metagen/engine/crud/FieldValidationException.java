package io.metagen.engine.crud;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Raised when a record payload fails validation; carries one message per field. */
public class FieldValidationException extends IllegalArgumentException {

    private final Map<String, String> errors;

    public FieldValidationException(Map<String, String> errors) {
        super("Validation failed: " + errors);
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
