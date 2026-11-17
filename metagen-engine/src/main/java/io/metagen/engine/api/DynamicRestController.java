package io.metagen.engine.api;

import io.metagen.engine.crud.DynamicCrudService;
import io.metagen.engine.crud.RecordPage;
import io.metagen.engine.workflow.AvailableTransition;
import io.metagen.engine.workflow.TransitionRequest;
import io.metagen.engine.workflow.TransitionResult;
import io.metagen.engine.workflow.WorkflowEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime API: one generic set of endpoints serving every configured entity.
 *
 * <p>Errors are not handled here — exceptions carry their meaning and the
 * application's exception handler turns them into RFC 7807 responses
 * (404 unknown entity / record, 403 access denied, 400 validation, 409 conflict,
 * 422 workflow rule not satisfied).
 */
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Tag(name = "Runtime API", description = "Dynamic CRUD and workflow for configured entities")
public class DynamicRestController {

    private static final String FILTER_PREFIX = "f.";

    private final DynamicCrudService crudService;
    private final WorkflowEngine     workflowEngine;

    @GetMapping("/{entity}")
    @Operation(
        summary = "List records",
        description = "Paginated list. Sort with `sort=field,asc`. Filter on exact values with "
                    + "`f.<field>=value` (e.g. `f.state=SUBMITTED`). `search` matches searchable text fields.")
    public RecordPage<Map<String, Object>> list(
        @Parameter(description = "Entity technical name, e.g. supplier")
        @PathVariable String entity,
        @RequestParam(defaultValue = "0")  int    page,
        @RequestParam(defaultValue = "25") int    size,
        @RequestParam(required = false)    String search,
        @RequestParam(required = false)    String sort,
        @RequestParam Map<String, String> allParams,
        Authentication auth
    ) {
        String sortField = null;
        String sortDir   = "DESC";
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            sortField = parts[0].trim();
            sortDir   = parts.length > 1 ? parts[1].trim() : "ASC";
        }
        Map<String, String> filters = new LinkedHashMap<>();
        allParams.forEach((key, value) -> {
            if (key.startsWith(FILTER_PREFIX) && value != null && !value.isBlank()) {
                filters.put(key.substring(FILTER_PREFIX.length()), value);
            }
        });
        return crudService.list(entity, page, size, search, sortField, sortDir,
                                filters, roles(auth), auth.getName());
    }

    @GetMapping("/{entity}/{id}")
    @Operation(summary = "Get one record")
    public Map<String, Object> getOne(@PathVariable String entity, @PathVariable UUID id,
                                      Authentication auth) {
        return crudService.findById(entity, id, roles(auth), auth.getName());
    }

    @PostMapping("/{entity}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a record")
    public Map<String, Object> create(@PathVariable String entity,
                                      @RequestBody Map<String, Object> body,
                                      Authentication auth) {
        return crudService.create(entity, body, roles(auth), auth.getName());
    }

    @PutMapping("/{entity}/{id}")
    @Operation(summary = "Update a record", description = "Partial update: only the fields present in the body change.")
    public Map<String, Object> update(@PathVariable String entity, @PathVariable UUID id,
                                      @RequestBody Map<String, Object> body,
                                      Authentication auth) {
        return crudService.update(entity, id, body, roles(auth), auth.getName());
    }

    @DeleteMapping("/{entity}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a record")
    public void delete(@PathVariable String entity, @PathVariable UUID id, Authentication auth) {
        crudService.delete(entity, id, roles(auth), auth.getName());
    }

    // ── Workflow ──────────────────────────────────────────────────────────

    @GetMapping("/{entity}/{id}/transitions")
    @Operation(summary = "Transitions the current user may trigger on this record")
    public List<AvailableTransition> transitions(@PathVariable String entity, @PathVariable UUID id,
                                                 Authentication auth) {
        return workflowEngine.available(entity, id, roles(auth), auth.getName());
    }

    @PostMapping("/{entity}/{id}/transition/{transitionId}")
    @Operation(
        summary = "Trigger a workflow transition",
        description = "Moves the record to the transition's target state. Optional body: {\"comment\": \"…\"}.")
    public TransitionResult transition(@PathVariable String entity, @PathVariable UUID id,
                                       @PathVariable UUID transitionId,
                                       @RequestBody(required = false) TransitionBody body,
                                       Authentication auth) {
        return workflowEngine.execute(new TransitionRequest(
            entity, id, transitionId, body != null ? body.comment() : null,
            roles(auth), auth.getName()));
    }

    @GetMapping("/{entity}/{id}/history")
    @Operation(summary = "Workflow history of a record")
    public List<Map<String, Object>> history(@PathVariable String entity, @PathVariable UUID id,
                                             Authentication auth) {
        return workflowEngine.history(entity, id, roles(auth), auth.getName());
    }

    public record TransitionBody(String comment) {}

    public static List<String> roles(Authentication auth) {
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
            .toList();
    }
}
