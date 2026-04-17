package io.metagen.engine.config;

import io.metagen.core.domain.*;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * YAML-serialisable representation of the full platform configuration.
 *
 * <p>This DTO mirrors the domain model but is designed for human-readable
 * YAML export/import. Every field uses simple types (String, boolean, List)
 * so Jackson's YAML mapper can handle them without custom serialisers.</p>
 *
 * <h2>Example output</h2>
 * <pre>
 * entities:
 *   - name: supplier
 *     label: Fournisseur
 *     softDelete: true
 *     auditable: true
 *     workflow: supplier_validation
 *     fields:
 *       - name: name
 *         label: Raison sociale
 *         type: STRING
 *         required: true
 *     permissions:
 *       - role: BUYER
 *         canRead: true
 *         canWrite: true
 * workflows:
 *   - name: supplier_validation
 *     label: Validation fournisseur
 *     states:
 *       - name: DRAFT
 *         label: Brouillon
 *         initial: true
 *         color: gray
 *     transitions:
 *       - from: DRAFT
 *         to: SUBMITTED
 *         label: Soumettre
 *         requiredRole: USER
 * roles:
 *   - name: BUYER
 *     label: Acheteur
 * </pre>
 */
@Data
public class MetaYamlDto {

    private List<EntityYaml>   entities  = List.of();
    private List<WorkflowYaml> workflows = List.of();
    private List<RoleYaml>     roles     = List.of();

    // ── Entity ────────────────────────────────────────────────────────

    @Data
    public static class EntityYaml {
        private String            name;
        private String            label;
        private String            description;
        private boolean           softDelete = true;
        private boolean           auditable  = true;
        private String            workflow;          // workflow name (reference)
        private List<FieldYaml>      fields      = List.of();
        private List<PermissionYaml> permissions = List.of();
        private List<ViewYaml>       views       = List.of();

        public static EntityYaml from(MetaEntity e) {
            var y = new EntityYaml();
            y.name        = e.getName();
            y.label       = e.getLabel();
            y.description = e.getDescription();
            y.softDelete  = e.isSoftDelete();
            y.auditable   = e.isAuditable();
            y.workflow    = e.getWorkflow() != null ? e.getWorkflow().getName() : null;
            y.fields = e.getFields().stream().map(FieldYaml::from).toList();
            y.permissions = e.getWorkflow() != null
                ? List.of() // loaded separately
                : List.of();
            return y;
        }
    }

    @Data
    public static class FieldYaml {
        private String  name;
        private String  label;
        private String  type;           // MetaFieldType name
        private boolean required;
        private boolean unique;
        private boolean searchable = true;
        private String  defaultValue;
        private Map<String, Object> options;
        private int     position;

        public static FieldYaml from(MetaField f) {
            var y = new FieldYaml();
            y.name         = f.getName();
            y.label        = f.getLabel();
            y.type         = f.getType().name();
            y.required     = f.isRequired();
            y.unique       = f.isUnique();
            y.searchable   = f.isSearchable();
            y.defaultValue = f.getDefaultValue();
            y.position     = f.getPosition();
            return y;
        }
    }

    @Data
    public static class PermissionYaml {
        private String  role;
        private boolean canRead;
        private boolean canWrite;
        private boolean canDelete;
        private String  rowFilterScript;

        public static PermissionYaml from(MetaPermission p) {
            var y = new PermissionYaml();
            y.role            = p.getRole().getName();
            y.canRead         = p.isCanRead();
            y.canWrite        = p.isCanWrite();
            y.canDelete       = p.isCanDelete();
            y.rowFilterScript = p.getRowFilterScript();
            return y;
        }
    }

    @Data
    public static class ViewYaml {
        private String              name;
        private String              label;
        private String              type;       // MetaViewType name
        private boolean             defaultView;
        private Map<String, Object> config;

        public static ViewYaml from(MetaView v) {
            var y = new ViewYaml();
            y.name        = v.getName();
            y.label       = v.getLabel();
            y.type        = v.getType().name();
            y.defaultView = v.isDefaultView();
            return y;
        }
    }

    // ── Workflow ──────────────────────────────────────────────────────

    @Data
    public static class WorkflowYaml {
        private String             name;
        private String             label;
        private String             description;
        private List<StateYaml>      states      = List.of();
        private List<TransitionYaml> transitions = List.of();

        public static WorkflowYaml from(MetaWorkflow w) {
            var y = new WorkflowYaml();
            y.name        = w.getName();
            y.label       = w.getLabel();
            y.description = w.getDescription();
            y.states = w.getStates().stream().map(StateYaml::from).toList();
            y.transitions = w.getTransitions().stream().map(TransitionYaml::from).toList();
            return y;
        }
    }

    @Data
    public static class StateYaml {
        private String  name;
        private String  label;
        private boolean initial;
        private boolean terminal;
        private String  color;
        private int     position;

        public static StateYaml from(MetaState s) {
            var y = new StateYaml();
            y.name     = s.getName();
            y.label    = s.getLabel();
            y.initial  = s.isInitial();
            y.terminal = s.isTerminal();
            y.color    = s.getColor();
            y.position = s.getPosition();
            return y;
        }
    }

    @Data
    public static class TransitionYaml {
        private String from;
        private String to;
        private String label;
        private String conditionScript;
        private String actionScript;
        private String requiredRole;

        public static TransitionYaml from(MetaTransition t) {
            var y = new TransitionYaml();
            y.from            = t.getFromState().getName();
            y.to              = t.getToState().getName();
            y.label           = t.getLabel();
            y.conditionScript = t.getConditionScript();
            y.actionScript    = t.getActionScript();
            y.requiredRole    = t.getRequiredRole();
            return y;
        }
    }

    // ── Role ─────────────────────────────────────────────────────────

    @Data
    public static class RoleYaml {
        private String name;
        private String label;
        private String description;

        public static RoleYaml from(MetaRole r) {
            var y = new RoleYaml();
            y.name        = r.getName();
            y.label       = r.getLabel();
            y.description = r.getDescription();
            return y;
        }
    }
}
