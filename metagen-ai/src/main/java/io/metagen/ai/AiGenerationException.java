package io.metagen.ai;

/** Thrown when the AI assistant fails to generate a valid proposal. */
public class AiGenerationException extends RuntimeException {
    public AiGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
