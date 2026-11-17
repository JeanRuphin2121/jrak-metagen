package io.metagen.meta.controller;

import io.metagen.ai.MetaProposal;
import io.metagen.engine.config.MetaYamlDto;
import io.metagen.engine.config.MetaYamlExporter;
import io.metagen.engine.config.ImportResult;
import io.metagen.engine.config.MetaYamlImporter;
import io.metagen.meta.service.MetaEntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Studio REST API — export and import the platform configuration as YAML.
 *
 * <h2>Typical CI/CD usage</h2>
 * <pre>
 * # Export current config to a file (versionned in Git)
 * curl -H "Authorization: Bearer $TOKEN" \
 *      https://api.myapp.com/api/studio/config/export \
 *      > config/metagen-config.yml
 *
 * # Import config on a fresh environment
 * curl -X POST -H "Authorization: Bearer $TOKEN" \
 *      -H "Content-Type: text/plain" \
 *      --data-binary @config/metagen-config.yml \
 *      https://api.newenv.com/api/studio/config/import
 * </pre>
 */
@RestController
@RequestMapping("/api/studio/config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Studio API — Config", description = "Export / import configuration as YAML (ADMIN only)")
public class MetaConfigController {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    private final MetaYamlExporter exporter;
    private final MetaYamlImporter importer;
    private final MetaEntityService entityService;

    @GetMapping(value = "/export", produces = "text/yaml")
    @Operation(
        summary = "Export full configuration as YAML",
        description = "Returns all entities, workflows, roles, and permissions as a versionnable YAML string.")
    public ResponseEntity<String> export() {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/yaml;charset=UTF-8"))
            .body(exporter.exportAll());
    }

    @GetMapping(value = "/export/{entityName}", produces = "text/yaml")
    @Operation(summary = "Export a single entity configuration as YAML")
    public ResponseEntity<String> exportEntity(@PathVariable String entityName) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/yaml;charset=UTF-8"))
            .body(exporter.exportEntity(entityName));
    }

    @PostMapping(value = "/import", consumes = "text/plain")
    @Operation(
        summary = "Import configuration from YAML",
        description = "Upserts entities, workflows, roles, and permissions. Non-destructive — never deletes. Use dryRun=true to validate without writing, deploy=true to create/alter tables afterwards.")
    public Map<String, Object> importConfig(@RequestBody String yaml,
                                            @RequestParam(defaultValue = "false") boolean dryRun,
                                            @RequestParam(defaultValue = "false") boolean deploy) {
        ImportResult result = importer.importYaml(yaml, dryRun);
        Map<String, Object> out = new LinkedHashMap<>(result.toMap());
        if (deploy && !dryRun) {
            out.put("deployed", entityService.deployAll().stream().map(MetaEntityController::toMap).toList());
        }
        return out;
    }

    @PostMapping("/import-proposal")
    @Operation(
        summary = "Import an AI-generated proposal",
        description = "Converts a MetaProposal (from POST /api/ai/generate) into metadata. Non-destructive.")
    public Map<String, Object> importProposal(@RequestBody MetaProposal proposal,
                                              @RequestParam(defaultValue = "false") boolean dryRun) {
        MetaYamlDto dto = proposalToDto(proposal);
        return importer.importDto(dto, dryRun).toMap();
    }

    // ── Conversion ────────────────────────────────────────────────────

    private static MetaYamlDto proposalToDto(MetaProposal proposal) {
        var dto = new MetaYamlDto();

        // Workflows
        if (proposal.getWorkflows() != null) {
            dto.setWorkflows(proposal.getWorkflows().stream()
                .map(MetaConfigController::workflowToYaml)
                .toList());
        }

        // Entities
        if (proposal.getEntities() != null) {
            dto.setEntities(proposal.getEntities().stream()
                .map(MetaConfigController::entityToYaml)
                .toList());
        }

        return dto;
    }

    private static MetaYamlDto.WorkflowYaml workflowToYaml(MetaProposal.WorkflowProposal w) {
        var y = new MetaYamlDto.WorkflowYaml();
        y.setName(w.getName());
        y.setLabel(w.getLabel());
        if (w.getStates() != null) {
            y.setStates(w.getStates().stream().map(s -> {
                var sy = new MetaYamlDto.StateYaml();
                sy.setName(s.getName());
                sy.setLabel(s.getLabel() != null ? s.getLabel() : s.getName());
                sy.setInitial(s.isInitial());
                sy.setTerminal(s.isTerminal());
                sy.setColor(s.getColor());
                return sy;
            }).toList());
        }
        if (w.getTransitions() != null) {
            y.setTransitions(w.getTransitions().stream().map(t -> {
                var ty = new MetaYamlDto.TransitionYaml();
                ty.setFrom(t.getFrom());
                ty.setTo(t.getTo());
                ty.setLabel(t.getLabel());
                ty.setRequiredRole(t.getRequiredRole());
                return ty;
            }).toList());
        }
        return y;
    }

    private static MetaYamlDto.EntityYaml entityToYaml(MetaProposal.EntityProposal e) {
        var y = new MetaYamlDto.EntityYaml();
        y.setName(e.getName());
        y.setLabel(e.getLabel());
        y.setDescription(e.getDescription());
        y.setWorkflow(e.getWorkflow());
        if (e.getFields() != null) {
            y.setFields(e.getFields().stream().map(f -> {
                var fy = new MetaYamlDto.FieldYaml();
                fy.setName(f.getName());
                fy.setLabel(f.getLabel());
                fy.setType(f.getType());
                fy.setRequired(f.isRequired());
                fy.setUnique(f.isUnique());
                fy.setSearchable(f.isSearchable());
                fy.setOptions(f.getOptions() == null ? null : MAPPER.valueToTree(f.getOptions()));
                return fy;
            }).toList());
        }
        return y;
    }
}
