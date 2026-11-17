package io.metagen.meta.controller;

import io.metagen.engine.deploy.EntityDeployer;
import io.metagen.meta.dto.CreateEntityRequest;
import io.metagen.meta.dto.CreateFieldRequest;
import io.metagen.meta.dto.MetaEntityResponse;
import io.metagen.meta.dto.UpdateFieldRequest;
import io.metagen.meta.service.MetaEntityService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/studio/entities")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Studio API — Entities", description = "Define entities and fields, then deploy them (ADMIN only)")
public class MetaEntityController {

    private final MetaEntityService service;

    @GetMapping
    @Operation(summary = "List all entity definitions")
    public List<MetaEntityResponse> listAll() {
        return service.listAll().stream().map(MetaEntityResponse::from).toList();
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get an entity with its fields")
    public MetaEntityResponse getOne(@PathVariable String name) {
        return MetaEntityResponse.from(service.getByName(name));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create an entity definition",
        description = "Creates metadata only. Call /deploy to create the actual database table.")
    public MetaEntityResponse create(@Valid @RequestBody CreateEntityRequest req) {
        return MetaEntityResponse.from(service.create(req));
    }

    @PutMapping("/{name}")
    @Operation(summary = "Update entity label, description and options")
    public MetaEntityResponse update(@PathVariable String name, @Valid @RequestBody CreateEntityRequest req) {
        return MetaEntityResponse.from(service.update(name, req));
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an entity definition", description = "Removes metadata only. The data_* table is preserved.")
    public void delete(@PathVariable String name) {
        service.delete(name);
    }

    @PostMapping("/{name}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a field", description = "Adds the field to metadata. Call /deploy to add the column.")
    public MetaEntityResponse addField(@PathVariable String name, @Valid @RequestBody CreateFieldRequest req) {
        service.addField(name, req);
        return MetaEntityResponse.from(service.getByName(name));
    }

    @PutMapping("/{name}/fields/{fieldId}")
    @Operation(summary = "Update a field", description = "Name and type are immutable. Call /deploy to apply new constraints.")
    public MetaEntityResponse updateField(@PathVariable String name, @PathVariable UUID fieldId,
                                          @Valid @RequestBody UpdateFieldRequest req) {
        service.updateField(name, fieldId, req);
        return MetaEntityResponse.from(service.getByName(name));
    }

    @DeleteMapping("/{name}/fields/{fieldId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a field", description = "Removes field metadata. The column is NOT dropped from the table.")
    public void deleteField(@PathVariable String name, @PathVariable UUID fieldId) {
        service.deleteField(name, fieldId);
    }

    @PostMapping("/{name}/deploy")
    @Operation(
        summary = "Deploy entity to the database",
        description = "Creates the data table, or adds missing columns, unique indexes and foreign keys. "
                    + "Additive and idempotent: never drops anything; anything it cannot reconcile is reported in `warnings`.")
    public Map<String, Object> deploy(@PathVariable String name) {
        return toMap(service.deploy(name));
    }

    @PostMapping("/deploy-all")
    @Operation(summary = "Deploy every entity", description = "Foreign keys between entities are resolved in a second pass.")
    public List<Map<String, Object>> deployAll() {
        return service.deployAll().stream().map(MetaEntityController::toMap).toList();
    }

    static Map<String, Object> toMap(EntityDeployer.DeployResult r) {
        return Map.of(
            "entity",       r.entityName(),
            "action",       r.action().name(),
            "table",        r.tableName(),
            "addedColumns", r.addedColumns(),
            "warnings",     r.warnings(),
            "summary",      r.summary());
    }
}
