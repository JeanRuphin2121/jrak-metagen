package io.metagen.engine.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.core.domain.MetaFieldType;
import io.metagen.core.domain.MetaPermission;
import io.metagen.core.domain.MetaRole;
import io.metagen.core.domain.MetaState;
import io.metagen.core.domain.MetaTransition;
import io.metagen.core.domain.MetaView;
import io.metagen.core.domain.MetaViewType;
import io.metagen.core.domain.MetaWorkflow;
import io.metagen.core.repository.MetaEntityRepository;
import io.metagen.core.repository.MetaPermissionRepository;
import io.metagen.core.repository.MetaRoleRepository;
import io.metagen.core.repository.MetaViewRepository;
import io.metagen.core.repository.MetaWorkflowRepository;
import io.metagen.engine.expr.Condition;
import io.metagen.engine.meta.EntityFieldResolver;
import io.metagen.engine.meta.MetaModelService;
import io.metagen.engine.meta.NamingRules;
import io.metagen.engine.workflow.WorkflowActionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Applies a YAML configuration to the database: an <strong>upsert</strong>
 * keyed by name. Importing never deletes anything — what the file does not
 * mention is left alone (and flagged as a warning when that looks like drift).
 *
 * <p>The import is atomic and happens in three phases inside one transaction:
 * <ol>
 *   <li><b>structural validation</b> of the whole file (names, types, references
 *       inside the file) — every problem is reported at once;</li>
 *   <li><b>apply</b> (create / update);</li>
 *   <li><b>semantic validation</b> against the resulting model (conditions
 *       reference real fields, roles and actions exist). Any failure rolls the
 *       whole import back.</li>
 * </ol>
 * With {@code dryRun=true} the transaction is always rolled back, so the same
 * call serves as a "validate / what would change" check in CI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaYamlImporter {

    private final MetaRoleRepository       roleRepository;
    private final MetaWorkflowRepository   workflowRepository;
    private final MetaEntityRepository     entityRepository;
    private final MetaPermissionRepository permissionRepository;
    private final MetaViewRepository       viewRepository;
    private final WorkflowActionRegistry   actions;
    private final MetaModelService         metaModel;

    // ── Entry points ──────────────────────────────────────────────────────

    @Transactional
    public ImportResult importYaml(String yaml, boolean dryRun) {
        if (yaml == null || yaml.isBlank()) {
            throw new ConfigValidationException(List.of("The configuration is empty"));
        }
        MetaYamlDto dto;
        try {
            dto = MetaYamlCodec.YAML.readValue(yaml, MetaYamlDto.class);
        } catch (JsonProcessingException e) {
            throw new ConfigValidationException(List.of("Invalid YAML: " + e.getOriginalMessage()));
        }
        if (dto == null) {
            throw new ConfigValidationException(List.of("The configuration is empty"));
        }
        return importDto(dto, dryRun);
    }

    @Transactional
    public ImportResult importDto(MetaYamlDto dto, boolean dryRun) {
        var result = new ImportResult();
        result.dryRun(dryRun);

        List<String> errors = new ArrayList<>();
        validateStructure(dto, errors);
        if (!errors.isEmpty()) throw new ConfigValidationException(errors);

        for (var r : dto.getRoles())     applyRole(r, result);
        roleRepository.flush();
        for (var w : dto.getWorkflows()) applyWorkflow(w, result, errors);
        for (var e : dto.getEntities())  applyEntity(e, result, errors);
        for (var e : dto.getEntities())  applyPermissions(e, result, errors);
        for (var e : dto.getEntities())  applyViews(e, result, errors);
        if (!errors.isEmpty()) throw new ConfigValidationException(errors);

        entityRepository.flush();
        validateSemantics(dto, errors);
        if (!errors.isEmpty()) throw new ConfigValidationException(errors);

        metaModel.evictAll();
        if (dryRun) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        log.info("Config import {}: {}", dryRun ? "(dry run)" : "applied", result.summary());
        return result;
    }

    // ── Phase 1 — structure ───────────────────────────────────────────────

    private void validateStructure(MetaYamlDto dto, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (var r : dto.getRoles()) {
            NamingRules.roleName(r.getName()).ifPresent(errors::add);
            if (r.getName() != null && !seen.add("role:" + r.getName())) {
                errors.add("role '" + r.getName() + "' is defined twice");
            }
        }
        for (var w : dto.getWorkflows()) {
            String at = "workflow '" + w.getName() + "'";
            NamingRules.workflowName(w.getName()).ifPresent(errors::add);
            if (w.getName() != null && !seen.add("workflow:" + w.getName())) {
                errors.add(at + " is defined twice");
            }
            if (w.getStates().isEmpty()) errors.add(at + " has no states");
            long initial = w.getStates().stream().filter(MetaYamlDto.StateYaml::isInitial).count();
            if (!w.getStates().isEmpty() && initial != 1) {
                errors.add(at + " must have exactly one initial state (found " + initial + ")");
            }
            Set<String> states = new HashSet<>();
            for (var s : w.getStates()) {
                NamingRules.stateName(s.getName()).ifPresent(m -> errors.add(at + ": " + m));
                if (s.getName() != null && !states.add(s.getName())) {
                    errors.add(at + ": state '" + s.getName() + "' is defined twice");
                }
            }
            Set<String> transitionKeys = new HashSet<>();
            for (var t : w.getTransitions()) {
                String tat = at + ", transition '" + t.getLabel() + "'";
                if (!transitionKeys.add(t.getFrom() + "→" + t.getTo() + "|" + t.getLabel())) {
                    errors.add(tat + ": another transition from '" + t.getFrom() + "' to '" + t.getTo()
                        + "' has the same label — labels must differ (e.g. 'Reject (manager)')");
                }
                if (t.getLabel() == null || t.getLabel().isBlank()) errors.add(at + ": a transition has no label");
                if (!states.contains(t.getFrom())) errors.add(tat + ": unknown state '" + t.getFrom() + "' in from");
                if (!states.contains(t.getTo()))   errors.add(tat + ": unknown state '" + t.getTo() + "' in to");
                if (t.getFrom() != null && w.getStates().stream()
                        .anyMatch(s -> s.getName().equals(t.getFrom()) && s.isTerminal())) {
                    errors.add(tat + ": starts from terminal state '" + t.getFrom() + "'");
                }
                if (t.getAction() != null && !t.getAction().isBlank()) {
                    try {
                        actions.requireKnown(t.getAction());
                    } catch (IllegalArgumentException e) {
                        errors.add(tat + ": " + e.getMessage());
                    }
                }
                try {
                    Condition.parse(t.getCondition());
                } catch (IllegalArgumentException e) {
                    errors.add(tat + ": invalid condition: " + e.getMessage());
                }
            }
        }
        for (var e : dto.getEntities()) {
            String at = "entity '" + e.getName() + "'";
            NamingRules.entityName(e.getName()).ifPresent(errors::add);
            if (e.getName() != null && !seen.add("entity:" + e.getName())) errors.add(at + " is defined twice");
            Set<String> fields = new HashSet<>();
            for (var f : e.getFields()) {
                NamingRules.fieldName(f.getName()).ifPresent(m -> errors.add(at + ": " + m));
                if (f.getName() != null && !fields.add(f.getName())) {
                    errors.add(at + ": field '" + f.getName() + "' is defined twice");
                }
                if (parseFieldType(f.getType()).isEmpty()) {
                    errors.add(at + ", field '" + f.getName() + "': unknown type '" + f.getType()
                        + "' (allowed: " + List.of(MetaFieldType.values()) + ")");
                }
            }
            for (var p : e.getPermissions()) {
                if (p.getRole() == null) errors.add(at + ": a permission has no role");
                try {
                    Condition.parse(p.getRowFilter());
                } catch (IllegalArgumentException ex) {
                    errors.add(at + ", permission '" + p.getRole() + "': invalid rowFilter: " + ex.getMessage());
                }
                validateMaskShape(p.getFieldMask()).ifPresent(m -> errors.add(at + ", permission '" + p.getRole() + "': " + m));
            }
            for (var v : e.getViews()) {
                NamingRules.viewName(v.getName()).ifPresent(m -> errors.add(at + ": " + m));
                if (!isViewType(v.getType())) {
                    errors.add(at + ", view '" + v.getName() + "': unknown type '" + v.getType()
                        + "' (allowed: " + List.of(MetaViewType.values()) + ")");
                }
                if (v.getName() != null && !seen.add("view:" + v.getName())) {
                    errors.add("view '" + v.getName() + "' is defined twice (view names are global)");
                }
            }
        }
    }

    private Optional<String> validateMaskShape(JsonNode mask) {
        if (mask == null || mask.isNull()) return Optional.empty();
        if (!mask.isObject()) return Optional.of("fieldMask must be an object {hidden: [...], readonly: [...]}");
        var names = mask.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            if (!key.equals("hidden") && !key.equals("readonly")) {
                return Optional.of("fieldMask has unknown key '" + key + "' (allowed: hidden, readonly)");
            }
            if (!mask.get(key).isArray()) return Optional.of("fieldMask." + key + " must be a list of field names");
        }
        return Optional.empty();
    }

    // ── Phase 2 — apply ───────────────────────────────────────────────────

    private void applyRole(MetaYamlDto.RoleYaml y, ImportResult result) {
        Optional<MetaRole> existing = roleRepository.findByName(y.getName());
        String label = firstNonBlank(y.getLabel(), y.getName());
        if (existing.isEmpty()) {
            roleRepository.save(MetaRole.builder()
                .name(y.getName()).label(label).description(y.getDescription()).builtIn(false).build());
            result.created("roles");
            return;
        }
        MetaRole role = existing.get();
        if (role.isBuiltIn()) {
            result.warn("role '" + role.getName() + "' is built-in — left unchanged");
            return;
        }
        boolean changed = !Objects.equals(role.getLabel(), label)
            || !Objects.equals(role.getDescription(), y.getDescription());
        role.setLabel(label);
        role.setDescription(y.getDescription());
        if (changed) { roleRepository.save(role); result.updated("roles"); }
    }

    private void applyWorkflow(MetaYamlDto.WorkflowYaml y, ImportResult result, List<String> errors) {
        Optional<MetaWorkflow> found = workflowRepository.findByName(y.getName())
            .map(w -> workflowRepository.findByIdWithAll(w.getId()).orElseThrow());
        boolean isNew = found.isEmpty();
        MetaWorkflow workflow = found.orElseGet(() -> MetaWorkflow.builder().name(y.getName()).build());

        boolean changed = isNew
            || !Objects.equals(workflow.getLabel(), firstNonBlank(y.getLabel(), y.getName()))
            || !Objects.equals(workflow.getDescription(), y.getDescription());
        workflow.setLabel(firstNonBlank(y.getLabel(), y.getName()));
        workflow.setDescription(y.getDescription());

        Map<String, MetaState> byName = new LinkedHashMap<>();
        workflow.getStates().forEach(s -> byName.put(s.getName(), s));
        Set<String> inFile = new HashSet<>();

        int index = 0;
        for (var sy : y.getStates()) {
            inFile.add(sy.getName());
            MetaState state = byName.get(sy.getName());
            int position = sy.getPosition() > 0 ? sy.getPosition() : index;
            String label = firstNonBlank(sy.getLabel(), sy.getName());
            if (state == null) {
                state = MetaState.builder().name(sy.getName()).label(label)
                    .initial(sy.isInitial()).terminal(sy.isTerminal())
                    .color(sy.getColor()).position(position).build();
                workflow.addState(state);
                byName.put(state.getName(), state);
                changed = true;
            } else {
                if (!Objects.equals(state.getLabel(), label) || state.isInitial() != sy.isInitial()
                    || state.isTerminal() != sy.isTerminal() || !Objects.equals(state.getColor(), sy.getColor())
                    || state.getPosition() != position) changed = true;
                state.setLabel(label);
                state.setInitial(sy.isInitial());
                state.setTerminal(sy.isTerminal());
                state.setColor(sy.getColor());
                state.setPosition(position);
            }
            index++;
        }
        for (MetaState state : byName.values()) {
            if (!inFile.contains(state.getName())) {
                if (state.isInitial()) { state.setInitial(false); changed = true; }
                result.warn("workflow '" + y.getName() + "': state '" + state.getName()
                    + "' is not in the file — kept (import never deletes)");
            }
        }
        workflow = workflowRepository.save(workflow);
        byName.clear();
        workflow.getStates().forEach(s -> byName.put(s.getName(), s));

        for (var ty : y.getTransitions()) {
            MetaState from = byName.get(ty.getFrom());
            MetaState to   = byName.get(ty.getTo());
            String conditionJson = MetaYamlCodec.toJson(ty.getCondition());
            String action = blankToNull(ty.getAction());
            MetaTransition existing = workflow.getTransitions().stream()
                .filter(t -> t.getFromState() == from && t.getToState() == to && t.getLabel().equals(ty.getLabel()))
                .findFirst().orElse(null);
            if (existing == null) {
                workflow.addTransition(MetaTransition.builder()
                    .fromState(from).toState(to).label(ty.getLabel())
                    .condition(conditionJson).actionName(action).requiredRole(blankToNull(ty.getRequiredRole()))
                    .build());
                changed = true;
            } else {
                if (!Objects.equals(existing.getCondition(), conditionJson)
                    || !Objects.equals(existing.getActionName(), action)
                    || !Objects.equals(existing.getRequiredRole(), blankToNull(ty.getRequiredRole()))) changed = true;
                existing.setCondition(conditionJson);
                existing.setActionName(action);
                existing.setRequiredRole(blankToNull(ty.getRequiredRole()));
            }
        }
        workflowRepository.save(workflow);
        if (isNew) result.created("workflows"); else if (changed) result.updated("workflows");
    }

    private void applyEntity(MetaYamlDto.EntityYaml y, ImportResult result, List<String> errors) {
        result.touchedEntity(y.getName());
        MetaEntity entity = entityRepository.findByNameWithAll(y.getName()).orElse(null);
        boolean isNew = entity == null;
        if (isNew) {
            entity = MetaEntity.builder()
                .name(y.getName()).tableName(MetaEntity.defaultTableName(y.getName())).build();
        }
        boolean changed = isNew
            || !Objects.equals(entity.getLabel(), firstNonBlank(y.getLabel(), y.getName()))
            || !Objects.equals(entity.getDescription(), y.getDescription())
            || entity.isSoftDelete() != y.isSoftDelete() || entity.isAuditable() != y.isAuditable();
        entity.setLabel(firstNonBlank(y.getLabel(), y.getName()));
        entity.setDescription(y.getDescription());
        entity.setSoftDelete(y.isSoftDelete());
        entity.setAuditable(y.isAuditable());

        if (y.getWorkflow() != null && !y.getWorkflow().isBlank()) {
            Optional<MetaWorkflow> wf = workflowRepository.findByName(y.getWorkflow());
            if (wf.isEmpty()) {
                errors.add("entity '" + y.getName() + "': unknown workflow '" + y.getWorkflow() + "'");
            } else {
                if (entity.getWorkflow() == null || !entity.getWorkflow().getId().equals(wf.get().getId())) changed = true;
                entity.setWorkflow(wf.get());
            }
        }

        Map<String, MetaField> byName = new LinkedHashMap<>();
        entity.getFields().forEach(f -> byName.put(f.getName(), f));
        Set<String> inFile = new HashSet<>();
        int index = 0;
        for (var fy : y.getFields()) {
            inFile.add(fy.getName());
            MetaFieldType type = parseFieldType(fy.getType()).orElseThrow();
            int position = fy.getPosition() > 0 ? fy.getPosition() : index;
            JsonNode options = fy.getOptions() != null && !fy.getOptions().isEmpty() ? fy.getOptions() : null;
            String label = firstNonBlank(fy.getLabel(), fy.getName());
            MetaField field = byName.get(fy.getName());
            if (field == null) {
                entity.addField(MetaField.builder()
                    .name(fy.getName()).label(label).type(type)
                    .required(fy.isRequired()).unique(fy.isUnique()).searchable(fy.isSearchable())
                    .defaultValue(fy.getDefaultValue()).options(options).position(position).build());
                result.created("fields");
                changed = true;
            } else {
                if (field.getType() != type) {
                    errors.add("entity '" + y.getName() + "', field '" + fy.getName() + "': type changed "
                        + field.getType() + " → " + type + " — changing a column's type is not automatic; "
                        + "add a new field and migrate the data");
                    continue;
                }
                boolean fieldChanged = !Objects.equals(field.getLabel(), label)
                    || field.isRequired() != fy.isRequired() || field.isUnique() != fy.isUnique()
                    || field.isSearchable() != fy.isSearchable()
                    || !Objects.equals(field.getDefaultValue(), fy.getDefaultValue())
                    || !Objects.equals(field.getOptions(), options) || field.getPosition() != position;
                field.setLabel(label);
                field.setRequired(fy.isRequired());
                field.setUnique(fy.isUnique());
                field.setSearchable(fy.isSearchable());
                field.setDefaultValue(fy.getDefaultValue());
                field.setOptions(options);
                field.setPosition(position);
                if (fieldChanged) { result.updated("fields"); changed = true; }
            }
            index++;
        }
        for (MetaField f : byName.values()) {
            if (!inFile.contains(f.getName())) {
                result.warn("entity '" + y.getName() + "': field '" + f.getName()
                    + "' is not in the file — kept (import never deletes)");
            }
        }
        entityRepository.save(entity);
        if (isNew) result.created("entities"); else if (changed) result.updated("entities");
    }

    private void applyPermissions(MetaYamlDto.EntityYaml y, ImportResult result, List<String> errors) {
        if (y.getPermissions().isEmpty()) return;
        MetaEntity entity = entityRepository.findByName(y.getName()).orElseThrow();
        for (var py : y.getPermissions()) {
            Optional<MetaRole> role = roleRepository.findByName(py.getRole());
            if (role.isEmpty()) {
                errors.add("entity '" + y.getName() + "': permission references unknown role '" + py.getRole() + "'");
                continue;
            }
            String rowFilter = MetaYamlCodec.toJson(py.getRowFilter());
            JsonNode mask = py.getFieldMask() != null && !py.getFieldMask().isEmpty() ? py.getFieldMask() : null;
            Optional<MetaPermission> existing =
                permissionRepository.findByRoleIdAndEntityId(role.get().getId(), entity.getId());
            MetaPermission perm = existing.orElseGet(() ->
                MetaPermission.builder().role(role.get()).entity(entity).build());
            boolean changed = existing.isEmpty()
                || perm.isCanRead() != py.isCanRead() || perm.isCanWrite() != py.isCanWrite()
                || perm.isCanDelete() != py.isCanDelete()
                || !Objects.equals(perm.getRowFilter(), rowFilter) || !Objects.equals(perm.getFieldMask(), mask);
            perm.setCanRead(py.isCanRead());
            perm.setCanWrite(py.isCanWrite());
            perm.setCanDelete(py.isCanDelete());
            perm.setRowFilter(rowFilter);
            perm.setFieldMask(mask);
            permissionRepository.save(perm);
            if (existing.isEmpty()) result.created("permissions"); else if (changed) result.updated("permissions");
        }
    }

    private void applyViews(MetaYamlDto.EntityYaml y, ImportResult result, List<String> errors) {
        if (y.getViews().isEmpty()) return;
        MetaEntity entity = entityRepository.findByNameWithAll(y.getName()).orElseThrow();
        for (var vy : y.getViews()) {
            MetaViewType type = MetaViewType.valueOf(vy.getType().trim().toUpperCase());
            JsonNode config = vy.getConfig() != null ? vy.getConfig()
                : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            Optional<MetaView> existing = viewRepository.findByName(vy.getName());
            if (existing.isPresent() && (existing.get().getEntity() == null
                    || !existing.get().getEntity().getId().equals(entity.getId()))) {
                errors.add("view '" + vy.getName() + "' already belongs to another entity");
                continue;
            }
            MetaView view = existing.orElseGet(() ->
                MetaView.builder().name(vy.getName()).entity(entity).build());
            boolean changed = existing.isEmpty()
                || !Objects.equals(view.getLabel(), firstNonBlank(vy.getLabel(), vy.getName()))
                || view.getType() != type || view.isDefaultView() != vy.isDefaultView()
                || !Objects.equals(view.getConfig(), config);
            view.setLabel(firstNonBlank(vy.getLabel(), vy.getName()));
            view.setType(type);
            view.setDefaultView(vy.isDefaultView());
            view.setConfig(config);
            viewRepository.save(view);
            if (existing.isEmpty()) result.created("views"); else if (changed) result.updated("views");
        }
    }

    // ── Phase 3 — semantics ───────────────────────────────────────────────

    private void validateSemantics(MetaYamlDto dto, List<String> errors) {
        Set<String> entityNames = new HashSet<>();
        entityRepository.findAll().forEach(e -> entityNames.add(e.getName()));

        for (var ey : dto.getEntities()) {
            MetaEntity entity = entityRepository.findByNameWithAll(ey.getName()).orElseThrow();
            var resolver = new EntityFieldResolver(entity);
            String at = "entity '" + ey.getName() + "'";

            for (MetaField f : entity.getFields()) {
                if (f.getType() == MetaFieldType.REFERENCE) {
                    JsonNode target = f.getOptions() != null ? f.getOptions().get("targetEntity") : null;
                    if (target == null || !entityNames.contains(target.asText())) {
                        errors.add(at + ", field '" + f.getName()
                            + "': REFERENCE needs options.targetEntity naming an existing entity");
                    }
                }
                if (f.getType() == MetaFieldType.ENUM && io.metagen.engine.crud.ValueCoercer
                        .allowedValues(f.getOptions()).isEmpty()) {
                    errors.add(at + ", field '" + f.getName() + "': ENUM needs options.values");
                }
            }
            for (var py : ey.getPermissions()) {
                try {
                    Condition c = Condition.parse(py.getRowFilter());
                    if (c != null) c.validate(resolver);
                } catch (IllegalArgumentException e) {
                    errors.add(at + ", permission '" + py.getRole() + "': " + e.getMessage());
                }
                if (py.getFieldMask() != null) {
                    for (String key : List.of("hidden", "readonly")) {
                        JsonNode names = py.getFieldMask().get(key);
                        if (names == null) continue;
                        names.forEach(n -> {
                            if (!resolver.exists(n.asText())) {
                                errors.add(at + ", permission '" + py.getRole() + "': fieldMask." + key
                                    + " references unknown field '" + n.asText() + "'");
                            }
                        });
                    }
                }
            }
            if (entity.getWorkflow() != null) {
                for (MetaTransition t : workflowRepository.findByIdWithAll(entity.getWorkflow().getId())
                        .orElseThrow().getTransitions()) {
                    try {
                        Condition c = Condition.parse(t.getCondition());
                        if (c != null) c.validate(resolver);
                    } catch (IllegalArgumentException e) {
                        errors.add(at + ", workflow '" + entity.getWorkflow().getName() + "', transition '"
                            + t.getLabel() + "': " + e.getMessage());
                    }
                }
            }
        }
        for (var wy : dto.getWorkflows()) {
            for (var ty : wy.getTransitions()) {
                String role = blankToNull(ty.getRequiredRole());
                if (role != null && !roleRepository.existsByName(role)) {
                    errors.add("workflow '" + wy.getName() + "', transition '" + ty.getLabel()
                        + "': unknown requiredRole '" + role + "'");
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static Optional<MetaFieldType> parseFieldType(String raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(MetaFieldType.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean isViewType(String raw) {
        if (raw == null) return false;
        try {
            MetaViewType.valueOf(raw.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
