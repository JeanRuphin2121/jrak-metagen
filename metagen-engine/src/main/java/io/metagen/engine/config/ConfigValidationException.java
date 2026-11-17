package io.metagen.engine.config;

import java.util.List;

/** The configuration is invalid; nothing was applied. Carries every problem found. */
public class ConfigValidationException extends IllegalArgumentException {

    private final List<String> errors;

    public ConfigValidationException(List<String> errors) {
        super(errors.size() + " configuration error(s): " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
