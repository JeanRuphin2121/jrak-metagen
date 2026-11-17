package io.metagen.meta.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateWorkflowRequest(
    @NotBlank @Size(max = 100)
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "name must be snake_case")
    String name,

    @NotBlank @Size(max = 200)
    String label,

    String description,

    @NotEmpty @Valid List<StateDto> states,

    @Valid List<TransitionDto> transitions
) {
    public CreateWorkflowRequest {
        if (transitions == null) transitions = List.of();
    }

    public record StateDto(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "name must be UPPER_SNAKE_CASE")
        String  name,
        @NotBlank String label,
        boolean initial,
        boolean terminal,
        String  color,
        int     position
    ) {}

    /**
     * @param condition    declarative guard, e.g. {@code {"field":"amount","op":"lte","value":5000}}
     * @param action       name of a registered {@code WorkflowAction} bean
     * @param requiredRole role needed to trigger the transition (none = anyone who can write)
     */
    public record TransitionDto(
        @NotBlank String fromState,
        @NotBlank String toState,
        @NotBlank String label,
        JsonNode condition,
        String   action,
        String   requiredRole
    ) {}
}
