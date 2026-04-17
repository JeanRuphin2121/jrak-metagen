package io.metagen.meta.controller;

import io.metagen.engine.deploy.EntityDeployer;
import io.metagen.meta.dto.CreateEntityRequest;
import io.metagen.meta.dto.CreateFieldRequest;
import io.metagen.meta.dto.MetaEntityResponse;
import io.metagen.meta.service.MetaEntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Studio REST API — manage entity and field metadata.
 *
 * <p>All endpoints require the {@code ADMIN} role.
 * These endpoints modify the platform's schema, not application data.</p>
 *
 * <h2>Typical Studio workflow</h2>
 * <pre>
 *   1. POST /api/studio/entities          — define entity + fields
 *   2. POST /api/studio/entities/{name}/deploy — create the data_* table
 *   3. [Users can now CRUD data via /api/data/{entity}]
 *   4. POST /api/studio/entities/{name}/fields — add a new field
 *   5. POST /api/studio/entities/{name}/deploy — add the column (safe migration)
 * </pre>
 */
@RestController
@RequestMapping("/api/studio/entities")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Studio API", description = "Manage entity and field metadata (ADMIN only)")
public class MetaEntityController {

    private final MetaEntityService service;

    // ── LIST ─────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all entities")
    public List<MetaEntityResponse> listAll() {
        return service.listAll().stream()
            .map(MetaEntityResponse::from)
            .toList();
    }

    // ── GET ONE ──────────────────────────────────────────────────────

    @GetMapping("/{name}")
    @Operation(summary = "Get entity with its fields")
    public ResponseEntity<MetaEntityResponse> getOne(@PathVariable String name) {
        try {
            return ResponseEntity.ok(MetaEntityResponse.from(service.getByName(name)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── CREATE ───────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create an entity definition",
        description = "Creates metadata only. Call /deploy to create the actual database table.")
    public MetaEntityResponse create(@Valid @RequestBody CreateEntityRequest req) {
        return MetaEntityResponse.from(service.create(req));
    }

    // ── UPDATE ───────────────────────────────────────────────────────

    @PutMapping("/{name}")
    @Operation(summary = "Update entity label / description / options")
    public ResponseEntity<MetaEntityResponse> update(
        @PathVariable String name,
        @Valid @RequestBody CreateEntityRequest req
    ) {
        try {
            return ResponseEntity.ok(MetaEntityResponse.from(service.update(name, req)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── DELETE ───────────────────────────────────────────────────────

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Delete entity definition",
        description = "Removes metadata only. The data_* table is preserved.")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        try {
            service.delete(name);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── FIELDS ───────────────────────────────────────────────────────

    @PostMapping("/{name}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Add a field to an entity",
        description = "Adds the field to metadata. Call /deploy to add the column to the table.")
    public MetaEntityResponse addField(
        @PathVariable String name,
        @Valid @RequestBody CreateFieldRequest req
    ) {
        service.addField(name, req);
        return MetaEntityResponse.from(service.getByName(name));
    }

    @DeleteMapping("/{name}/fields/{fieldId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Remove a field from an entity",
        description = "Removes field metadata. The column is NOT dropped from the data table.")
    public ResponseEntity<Void> deleteField(
        @PathVariable String name,
        @PathVariable UUID   fieldId
    ) {
        try {
            service.deleteField(name, fieldId);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── DEPLOY ───────────────────────────────────────────────────────

    @PostMapping("/{name}/deploy")
    @Operation(
        summary = "Deploy entity to database",
        description = "Creates the data_* table if it doesn't exist, or adds missing columns. Idempotent.")
    public Map<String, String> deploy(@PathVariable String name) {
        EntityDeployer.DeployResult result = service.deploy(name);
        return Map.of(
            "action",  result.action().name(),
            "table",   result.tableName(),
            "summary", result.summary()
        );
    }
}
