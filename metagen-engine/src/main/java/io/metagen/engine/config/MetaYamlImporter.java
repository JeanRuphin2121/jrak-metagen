package io.metagen.engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.metagen.core.domain.*;
import io.metagen.core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Imports a YAML configuration dump back into the database.
 *
 * <h2>Import strategy</h2>
 * <ul>
 *   <li><strong>Roles</strong> — upserted by name</li>
 *   <li><strong>Workflows</strong> — upserted by name;
 *       existing states and transitions are replaced entirely</li>
 *   <li><strong>Entities</strong> — upserted by name;
 *       existing fields are merged (add missing, update changed, never delete)</li>
 *   <li><strong>Permissions</strong> — upserted by (role, entity) pair</li>
 * </ul>
 *
 * <p>The importer is intentionally non-destructive:
 * it never deletes entities, fields, or workflows — only adds or updates.
 * This mirrors the {@link io.metagen.engine.deploy.EntityDeployer} philosophy.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * POST /api/studio/config/import
 * Content-Type: text/yaml
 * Body: (YAML config)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaYamlImporter {

    private final MetaRoleRepository       roleRepository;
    private final MetaWorkflowRepository   workflowRepository;
    private final MetaEntityRepository     entityRepository;
    private final MetaPermissionRepository permissionRepository;
    private final MetaFieldRepository      fieldRepository;

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    // ── Import ────────────────────────────────────────────────────────

    /**
     * Parses a YAML string and applies it to the database.
     *
     * @param yaml full YAML config as produced by {@link MetaYamlExporter}
     * @return summary of what was created/updated
     */
    @Transactional
    public ImportResult importYaml(String yaml) {
        MetaYamlDto dto;
        try {
            dto = YAML_MAPPER.readValue(yaml, MetaYamlDto.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid YAML: " + e.getMessage(), e);
        }

        var result = new ImportResult();

        importRoles(dto.getRoles(), result);
        importWorkflows(dto.getWorkflows(), result);
        importEntities(dto.getEntities(), result);

        log.info("Import complete: {}", result.summary());
        return result;
    }

    // ── Roles ─────────────────────────────────────────────────────────

    private void importRoles(List<MetaYamlDto.RoleYaml> roles, ImportResult result) {
        if (roles == null) return;
        for (var r : roles) {
            boolean exists = roleRepository.existsByName(r.getName());
            if (!exists) {
                roleRepository.save(MetaRole.builder()
                    .name(r.getName())
                    .label(r.getLabel())
                    .description(r.getDescription())
                    .builtIn(false)
                    .build());
                result.rolesCreated++;
            } else {
                result.rolesSkipped++;
            }
        }
    }

    // ── Workflows ─────────────────────────────────────────────────────

    private void importWorkflows(List<MetaYamlDto.WorkflowYaml> workflows, ImportResult result) {
        if (workflows == null) return;
        for (var w : workflows) {
            Optional<MetaWorkflow> existing = workflowRepository.findByName(w.getName());
            if (existing.isEmpty()) {
                createWorkflow(w);
                result.workflowsCreated++;
            } else {
                result.workflowsSkipped++;
            }
        }
    }

    private MetaWorkflow createWorkflow(MetaYamlDto.WorkflowYaml w) {
        MetaWorkflow workflow = MetaWorkflow.builder()
            .name(w.getName())
            .label(w.getLabel())
            .description(w.getDescription())
            .build();

        if (w.getStates() != null) {
            for (var s : w.getStates()) {
                workflow.addState(MetaState.builder()
                    .name(s.getName()).label(s.getLabel())
                    .initial(s.isInitial()).terminal(s.isTerminal())
                    .color(s.getColor()).position(s.getPosition())
                    .build());
            }
        }

        MetaWorkflow saved = workflowRepository.save(workflow);

        // Wire transitions after states have IDs
        if (w.getTransitions() != null) {
            Map<String, MetaState> stateMap = saved.getStates().stream()
                .collect(Collectors.toMap(MetaState::getName, Function.identity()));

            for (var t : w.getTransitions()) {
                MetaState from = stateMap.get(t.getFrom());
                MetaState to   = stateMap.get(t.getTo());
                if (from == null || to == null) {
                    log.warn("Skipping transition {} → {}: state not found", t.getFrom(), t.getTo());
                    continue;
                }
                saved.addTransition(MetaTransition.builder()
                    .fromState(from).toState(to).label(t.getLabel())
                    .conditionScript(t.getConditionScript())
                    .actionScript(t.getActionScript())
                    .requiredRole(t.getRequiredRole())
                    .build());
            }
            saved = workflowRepository.save(saved);
        }
        return saved;
    }

    // ── Entities ──────────────────────────────────────────────────────

    private void importEntities(List<MetaYamlDto.EntityYaml> entities, ImportResult result) {
        if (entities == null) return;
        for (var e : entities) {
            Optional<MetaEntity> existing = entityRepository.findByName(e.getName());
            if (existing.isEmpty()) {
                createEntity(e);
                result.entitiesCreated++;
            } else {
                mergeEntityFields(existing.get(), e, result);
                result.entitiesUpdated++;
            }
            importPermissions(e, result);
        }
    }

    private void createEntity(MetaYamlDto.EntityYaml e) {
        MetaWorkflow workflow = null;
        if (e.getWorkflow() != null) {
            workflow = workflowRepository.findByName(e.getWorkflow()).orElse(null);
        }

        MetaEntity entity = MetaEntity.builder()
            .name(e.getName())
            .label(e.getLabel())
            .tableName(MetaEntity.defaultTableName(e.getName()))
            .description(e.getDescription())
            .softDelete(e.isSoftDelete())
            .auditable(e.isAuditable())
            .workflow(workflow)
            .build();

        if (e.getFields() != null) {
            for (int i = 0; i < e.getFields().size(); i++) {
                var f = e.getFields().get(i);
                entity.addField(MetaField.builder()
                    .name(f.getName()).label(f.getLabel())
                    .type(MetaFieldType.valueOf(f.getType()))
                    .required(f.isRequired()).unique(f.isUnique())
                    .searchable(f.isSearchable())
                    .defaultValue(f.getDefaultValue())
                    .position(f.getPosition() > 0 ? f.getPosition() : i)
                    .build());
            }
        }
        entityRepository.save(entity);
    }

    private void mergeEntityFields(MetaEntity entity,
                                    MetaYamlDto.EntityYaml yaml,
                                    ImportResult result) {
        if (yaml.getFields() == null) return;
        Set<String> existingNames = entity.getFields().stream()
            .map(MetaField::getName).collect(Collectors.toSet());

        for (var f : yaml.getFields()) {
            if (!existingNames.contains(f.getName())) {
                entity.addField(MetaField.builder()
                    .name(f.getName()).label(f.getLabel())
                    .type(MetaFieldType.valueOf(f.getType()))
                    .required(f.isRequired()).unique(f.isUnique())
                    .searchable(f.isSearchable())
                    .defaultValue(f.getDefaultValue())
                    .position(f.getPosition())
                    .build());
                result.fieldsAdded++;
            }
        }
        entityRepository.save(entity);
    }

    private void importPermissions(MetaYamlDto.EntityYaml e, ImportResult result) {
        if (e.getPermissions() == null) return;
        MetaEntity entity = entityRepository.findByName(e.getName()).orElse(null);
        if (entity == null) return;

        for (var p : e.getPermissions()) {
            MetaRole role = roleRepository.findByName(p.getRole()).orElse(null);
            if (role == null) {
                log.warn("Permission references unknown role '{}' — skipping", p.getRole());
                continue;
            }
            MetaPermission perm = permissionRepository
                .findByRoleIdAndEntityId(role.getId(), entity.getId())
                .orElseGet(() -> MetaPermission.builder().role(role).entity(entity).build());

            perm.setCanRead(p.isCanRead());
            perm.setCanWrite(p.isCanWrite());
            perm.setCanDelete(p.isCanDelete());
            perm.setRowFilterScript(p.getRowFilterScript());
            permissionRepository.save(perm);
            result.permissionsUpserted++;
        }
    }

    // ── Result ────────────────────────────────────────────────────────

    public static class ImportResult {
        int rolesCreated, rolesSkipped;
        int workflowsCreated, workflowsSkipped;
        int entitiesCreated, entitiesUpdated;
        int fieldsAdded, permissionsUpserted;

        public String summary() {
            return String.format(
                "roles: +%d (skipped %d) | workflows: +%d (skipped %d) | " +
                "entities: +%d updated %d | fields added: %d | permissions: %d",
                rolesCreated, rolesSkipped,
                workflowsCreated, workflowsSkipped,
                entitiesCreated, entitiesUpdated,
                fieldsAdded, permissionsUpserted);
        }

        public Map<String, Object> toMap() {
            return Map.of(
                "rolesCreated",        rolesCreated,
                "workflowsCreated",    workflowsCreated,
                "entitiesCreated",     entitiesCreated,
                "entitiesUpdated",     entitiesUpdated,
                "fieldsAdded",         fieldsAdded,
                "permissionsUpserted", permissionsUpserted
            );
        }
    }
}
