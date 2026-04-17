package io.metagen.meta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for creating a workflow with its states and transitions.
 *
 * @param name        snake_case technical name
 * @param label       human-readable label
 * @param description optional description
 * @param states      list of states (at least one must be initial)
 * @param transitions list of transitions between states
 */
public record CreateWorkflowRequest(

    @NotBlank @Size(max = 100)
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "name must be snake_case")
    String name,

    @NotBlank @Size(max = 200)
    String label,

    String description,

    @Valid List<StateDto>      states,
    @Valid List<TransitionDto> transitions

) {
    public record StateDto(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "name must be UPPER_SNAKE_CASE")
        String  name,
        @NotBlank String  label,
        boolean initial,
        boolean terminal,
        String  color,
        int     position
    ) {}

    public record TransitionDto(
        @NotBlank String fromState,
        @NotBlank String toState,
        @NotBlank String label,
        String conditionScript,
        String actionScript,
        String requiredRole
    ) {}
}
