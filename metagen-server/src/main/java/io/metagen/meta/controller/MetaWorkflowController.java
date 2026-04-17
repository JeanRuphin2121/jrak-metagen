package io.metagen.meta.controller;

import io.metagen.core.domain.MetaState;
import io.metagen.core.domain.MetaTransition;
import io.metagen.core.domain.MetaWorkflow;
import io.metagen.meta.dto.CreateWorkflowRequest;
import io.metagen.meta.service.MetaWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Studio REST API — manage workflow definitions.
 *
 * <h2>Typical workflow</h2>
 * <pre>
 *   1. POST  /api/studio/workflows               — create workflow + states + transitions
 *   2. POST  /api/studio/workflows/{name}/attach  — attach to an entity
 *   3. POST  /api/studio/entities/{name}/deploy   — add state column to data_* table
 * </pre>
 */
@RestController
@RequestMapping("/api/studio/workflows")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Studio API — Workflows", description = "Manage workflow state machines (ADMIN only)")
public class MetaWorkflowController {

    private final MetaWorkflowService service;

    @GetMapping
    @Operation(summary = "List all workflows")
    public List<WorkflowSummary> listAll() {
        return service.listAll().stream().map(WorkflowSummary::from).toList();
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get workflow with all states and transitions")
    public ResponseEntity<WorkflowDetail> getOne(@PathVariable String name) {
        try {
            return ResponseEntity.ok(WorkflowDetail.from(service.getByName(name)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a workflow with states and transitions")
    public WorkflowDetail create(@Valid @RequestBody CreateWorkflowRequest req) {
        return WorkflowDetail.from(service.create(req));
    }

    @PostMapping("/{name}/attach")
    @Operation(summary = "Attach workflow to an entity")
    public Map<String, String> attach(
        @PathVariable String name,
        @RequestParam  String entityName
    ) {
        service.attachToEntity(name, entityName);
        return Map.of("message", "Workflow '" + name + "' attached to entity '" + entityName + "'");
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a workflow")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        try {
            service.delete(name);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Response DTOs ─────────────────────────────────────────────────

    record WorkflowSummary(UUID id, String name, String label, int stateCount, Instant createdAt) {
        static WorkflowSummary from(MetaWorkflow w) {
            return new WorkflowSummary(w.getId(), w.getName(), w.getLabel(),
                                       w.getStates().size(), w.getCreatedAt());
        }
    }

    record WorkflowDetail(UUID id, String name, String label, String description,
                          List<StateDto> states, List<TransitionDto> transitions) {

        record StateDto(UUID id, String name, String label,
                        boolean initial, boolean terminal, String color, int position) {}

        record TransitionDto(UUID id, String fromState, String toState, String label,
                             String conditionScript, String actionScript, String requiredRole) {}

        static WorkflowDetail from(MetaWorkflow w) {
            var states = w.getStates().stream()
                .map(s -> new StateDto(s.getId(), s.getName(), s.getLabel(),
                                       s.isInitial(), s.isTerminal(), s.getColor(), s.getPosition()))
                .toList();
            var transitions = w.getTransitions().stream()
                .map(t -> new TransitionDto(t.getId(),
                                            t.getFromState().getName(), t.getToState().getName(),
                                            t.getLabel(), t.getConditionScript(),
                                            t.getActionScript(), t.getRequiredRole()))
                .toList();
            return new WorkflowDetail(w.getId(), w.getName(), w.getLabel(),
                                      w.getDescription(), states, transitions);
        }
    }
}
