package io.metagen.engine.api;

import io.metagen.engine.crud.DynamicCrudService;
import io.metagen.engine.crud.RecordPage;
import io.metagen.engine.workflow.TransitionRequest;
import io.metagen.engine.workflow.TransitionResult;
import io.metagen.engine.workflow.WorkflowEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Single controller handling the entire Runtime API.
 *
 * <p>Instead of generating one controller per entity, this single controller
 * captures {@code /api/data/{entity}/**} and delegates to
 * {@link DynamicCrudService}. The entity name from the URL path is the only
 * thing that varies — the CRUD logic is identical for every entity.</p>
 *
 * <h2>Endpoint summary</h2>
 * <pre>
 *   GET    /api/data/{entity}            — paginated list
 *   GET    /api/data/{entity}/{id}       — single record
 *   POST   /api/data/{entity}            — create
 *   PUT    /api/data/{entity}/{id}       — full or partial update
 *   DELETE /api/data/{entity}/{id}       — delete (soft or hard)
 *   POST   /api/data/{entity}/{id}/transition/{transitionId}  — workflow action
 * </pre>
 *
 * <h2>Authentication</h2>
 * All endpoints require a valid JWT Bearer token (enforced by Spring Security).
 * The roles extracted from the token are forwarded to the RBAC layer.
 */
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Tag(name = "Runtime API", description = "Dynamic CRUD for configured entities")
public class DynamicRestController {

    private final DynamicCrudService crudService;
    private final WorkflowEngine     workflowEngine;

    // ── LIST ─────────────────────────────────────────────────────────

    @GetMapping("/{entity}")
    @Operation(summary = "List records", description = "Returns a paginated list of records for the given entity.")
    public RecordPage<Map<String, Object>> list(
        @Parameter(description = "Entity technical name, e.g. supplier")
        @PathVariable String entity,

        @RequestParam(defaultValue = "0")  int    page,
        @RequestParam(defaultValue = "25") int    size,
        @RequestParam(required = false)    String search,
        @RequestParam(required = false)    String sort,     // e.g. "name,asc"
        Authentication auth
    ) {
        String sortField = null;
        String sortDir   = "DESC";
        if (sort != null && sort.contains(",")) {
            var parts = sort.split(",", 2);
            sortField = parts[0].trim();
            sortDir   = parts[1].trim();
        }
        return crudService.list(entity, page, size, search, sortField, sortDir, roles(auth));
    }

    // ── GET ONE ──────────────────────────────────────────────────────

    @GetMapping("/{entity}/{id}")
    @Operation(summary = "Get one record")
    public ResponseEntity<Map<String, Object>> getOne(
        @PathVariable String entity,
        @PathVariable UUID   id,
        Authentication auth
    ) {
        try {
            return ResponseEntity.ok(crudService.findById(entity, id, roles(auth)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── CREATE ───────────────────────────────────────────────────────

    @PostMapping("/{entity}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a record")
    public Map<String, Object> create(
        @PathVariable               String              entity,
        @RequestBody                Map<String, Object> body,
        Authentication auth
    ) {
        return crudService.create(entity, body, roles(auth), auth.getName());
    }

    // ── UPDATE ───────────────────────────────────────────────────────

    @PutMapping("/{entity}/{id}")
    @Operation(summary = "Update a record (partial update supported)")
    public ResponseEntity<Map<String, Object>> update(
        @PathVariable String              entity,
        @PathVariable UUID                id,
        @RequestBody  Map<String, Object> body,
        Authentication auth
    ) {
        try {
            return ResponseEntity.ok(
                crudService.update(entity, id, body, roles(auth), auth.getName()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── DELETE ───────────────────────────────────────────────────────

    @DeleteMapping("/{entity}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a record")
    public ResponseEntity<Void> delete(
        @PathVariable String entity,
        @PathVariable UUID   id,
        Authentication auth
    ) {
        try {
            crudService.delete(entity, id, roles(auth), auth.getName());
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── WORKFLOW TRANSITION ───────────────────────────────────────────

    @PostMapping("/{entity}/{id}/transition/{transitionId}")
    @Operation(
        summary = "Trigger a workflow transition",
        description = "Moves the record to the next state if conditions are met.")
    public ResponseEntity<TransitionResult> transition(
        @PathVariable String entity,
        @PathVariable UUID   id,
        @PathVariable UUID   transitionId,
        @RequestBody(required = false) Map<String, Object> context,
        Authentication auth
    ) {
        try {
            var request = new TransitionRequest(entity, id, transitionId,
                                                context != null ? context : Map.of(),
                                                roles(auth), auth.getName());
            return ResponseEntity.ok(workflowEngine.execute(request));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            // Transition not allowed (wrong state, failed condition, missing role)
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Extracts role names from the JWT-backed Authentication. */
    private List<String> roles(Authentication auth) {
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
            .toList();
    }
}
