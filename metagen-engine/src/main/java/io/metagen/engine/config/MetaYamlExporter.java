package io.metagen.engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.metagen.core.domain.*;
import io.metagen.core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * Exports the full platform configuration to YAML.
 *
 * <h2>Why this matters</h2>
 * Simplicité stores config in a database — you can't {@code git diff} it,
 * review it in a PR, or roll it back with {@code git revert}.
 *
 * Metagen's YAML export gives you:
 * <ul>
 *   <li>Human-readable configuration files you can read without a DB client</li>
 *   <li>Git-versionnable history of every schema change</li>
 *   <li>PR reviews on entity/workflow/permission changes</li>
 *   <li>Reproducible environments: {@code metagen import} on a fresh DB</li>
 *   <li>Environment diff: {@code metagen diff prod staging}</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * GET /api/studio/config/export          → YAML string
 * GET /api/studio/config/export?pretty   → indented YAML
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaYamlExporter {

    private final MetaEntityRepository     entityRepository;
    private final MetaWorkflowRepository   workflowRepository;
    private final MetaRoleRepository       roleRepository;
    private final MetaPermissionRepository permissionRepository;

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
        new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
    );

    // ── Export to string ─────────────────────────────────────────────

    /**
     * Exports the entire configuration as a YAML string.
     *
     * @return YAML string representing entities, workflows, and roles
     */
    @Transactional(readOnly = true)
    public String exportAll() {
        MetaYamlDto dto = buildDto();
        try {
            StringWriter writer = new StringWriter();
            YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValue(writer, dto);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialise config to YAML", e);
        }
    }

    /**
     * Exports a single entity (with its permissions) as a YAML string.
     * Useful for per-entity config files.
     */
    @Transactional(readOnly = true)
    public String exportEntity(String entityName) {
        MetaEntity entity = entityRepository.findByNameWithAll(entityName)
            .orElseThrow(() -> new java.util.NoSuchElementException("Entity not found: " + entityName));

        MetaYamlDto.EntityYaml yaml = MetaYamlDto.EntityYaml.from(entity);

        // Attach permissions
        List<MetaPermission> perms = permissionRepository.findByEntityId(entity.getId());
        yaml.setPermissions(perms.stream().map(MetaYamlDto.PermissionYaml::from).toList());

        // Attach views
        yaml.setViews(entity.getViews().stream().map(MetaYamlDto.ViewYaml::from).toList());

        try {
            return YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(yaml);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialise entity to YAML", e);
        }
    }

    // ── Build DTO ─────────────────────────────────────────────────────

    private MetaYamlDto buildDto() {
        var dto = new MetaYamlDto();

        // Roles (exclude built-in ones — they are always seeded)
        List<MetaYamlDto.RoleYaml> roles = roleRepository.findByBuiltInFalse()
            .stream().map(MetaYamlDto.RoleYaml::from).toList();
        dto.setRoles(roles);

        // Workflows
        List<MetaYamlDto.WorkflowYaml> workflows = workflowRepository.findAll().stream()
            .map(w -> {
                // Reload with full graph (states + transitions)
                return workflowRepository.findByIdWithAll(w.getId())
                    .map(MetaYamlDto.WorkflowYaml::from)
                    .orElseThrow();
            })
            .toList();
        dto.setWorkflows(workflows);

        // Entities with fields + permissions
        List<MetaYamlDto.EntityYaml> entities = entityRepository.findAll().stream()
            .map(e -> {
                MetaEntity full = entityRepository.findByNameWithAll(e.getName()).orElseThrow();
                MetaYamlDto.EntityYaml yaml = MetaYamlDto.EntityYaml.from(full);
                List<MetaPermission> perms = permissionRepository.findByEntityId(full.getId());
                yaml.setPermissions(perms.stream().map(MetaYamlDto.PermissionYaml::from).toList());
                yaml.setViews(full.getViews().stream().map(MetaYamlDto.ViewYaml::from).toList());
                return yaml;
            })
            .toList();
        dto.setEntities(entities);

        log.debug("Exported config: {} entities, {} workflows, {} roles",
                  entities.size(), workflows.size(), roles.size());
        return dto;
    }
}
