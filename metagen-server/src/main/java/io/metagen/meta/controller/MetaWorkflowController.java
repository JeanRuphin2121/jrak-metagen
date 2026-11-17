package io.metagen.meta.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.metagen.core.domain.MetaState;
import io.metagen.core.domain.MetaTransition;
import io.metagen.core.domain.MetaWorkflow;
import io.metagen.engine.config.MetaYamlCodec;
import io.metagen.engine.workflow.WorkflowActionRegistry;
import io.metagen.meta.dto.CreateWorkflowRequest;
import io.metagen.meta.service.MetaWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/studio/workflows")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Studio API — Workflows", description = "Manage workflow state machines (ADMIN only)")
public class MetaWorkflowController {

    private final MetaWorkflowService     service;
    private final WorkflowActionRegistry  actions;

    @GetMapping
    @Operation(summary = "List all workflows")
    public List<WorkflowSummary> listAll() {
        return service.listAll().stream().map(WorkflowSummary::from).toList();
    }

    @GetMapping("/actions")
    @Operation(summary = "Names of the workflow actions (Java beans) available to transitions")
    public List<String> availableActions() {
        return List.copyOf(actions.names());
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get a workflow with its states and transitions")
    public WorkflowDetail getOne(@PathVariable String name) {
        return WorkflowDetail.from(service.getByName(name));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a workflow with states and transitions")
    public WorkflowDetail create(@Valid @RequestBody CreateWorkflowRequest req) {
        return WorkflowDetail.from(service.create(req));
    }

    @PutMapping("/{name}")
    @Operation(summary = "Update a workflow", description = "Merges states and transitions by name; nothing is deleted.")
    public WorkflowDetail update(@PathVariable String name, @Valid @RequestBody CreateWorkflowRequest req) {
        if (!name.equals(req.name())) throw new IllegalArgumentException("The workflow name cannot be changed");
        return WorkflowDetail.from(service.upsert(req));
    }

    @PostMapping("/{name}/attach")
    @Operation(summary = "Attach a workflow to an entity")
    public Map<String, String> attach(@PathVariable String name, @RequestParam String entityName) {
        service.attachToEntity(name, entityName);
        return Map.of("message", "Workflow '" + name + "' attached to entity '" + entityName + "'");
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a workflow (must not be attached to an entity)")
    public void delete(@PathVariable String name) {
        service.delete(name);
    }

    record WorkflowSummary(UUID id, String name, String label, int stateCount, Instant createdAt) {
        static WorkflowSummary from(MetaWorkflow w) {
            return new WorkflowSummary(w.getId(), w.getName(), w.getLabel(), w.getStates().size(), w.getCreatedAt());
        }
    }

    record WorkflowDetail(UUID id, String name, String label, String description,
                          List<StateDto> states, List<TransitionDto> transitions) {

        record StateDto(UUID id, String name, String label,
                        boolean initial, boolean terminal, String color, int position) {}

        record TransitionDto(UUID id, String fromState, String toState, String label,
                             JsonNode condition, String action, String requiredRole) {}

        static WorkflowDetail from(MetaWorkflow w) {
            var states = w.getStates().stream()
                .sorted(Comparator.comparingInt(MetaState::getPosition))
                .map(s -> new StateDto(s.getId(), s.getName(), s.getLabel(),
                                       s.isInitial(), s.isTerminal(), s.getColor(), s.getPosition()))
                .toList();
            var transitions = w.getTransitions().stream()
                .sorted(Comparator.comparing((MetaTransition t) -> t.getFromState().getPosition())
                    .thenComparing(t -> t.getToState().getPosition()))
                .map(t -> new TransitionDto(t.getId(), t.getFromState().getName(), t.getToState().getName(),
                                            t.getLabel(), MetaYamlCodec.parseJson(t.getCondition()),
                                            t.getActionName(), t.getRequiredRole()))
                .toList();
            return new WorkflowDetail(w.getId(), w.getName(), w.getLabel(), w.getDescription(), states, transitions);
        }
    }
}
