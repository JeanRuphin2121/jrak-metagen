package io.metagen.engine.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.core.domain.MetaPermission;
import io.metagen.core.domain.MetaRole;
import io.metagen.core.domain.MetaState;
import io.metagen.core.domain.MetaTransition;
import io.metagen.core.domain.MetaView;
import io.metagen.core.domain.MetaWorkflow;
import lombok.Data;

import java.util.Comparator;
import java.util.List;

/**
 * YAML shape of the whole platform configuration ("config as code").
 *
 * <p>Parsing is strict: an unknown key is an error, so a typo in a file under
 * version control is caught at import time instead of being silently ignored.
 * Properties equal to their default are omitted on export to keep diffs small.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class MetaYamlDto {

    private List<RoleYaml>     roles     = List.of();
    private List<WorkflowYaml> workflows = List.of();
    private List<EntityYaml>   entities  = List.of();

    @Data
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class RoleYaml {
        private String name;
        private String label;
        private String description;

        public static RoleYaml from(MetaRole r) {
            var y = new RoleYaml();
            y.name = r.getName();
            y.label = r.getLabel();
            y.description = r.getDescription();
            return y;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class WorkflowYaml {
        private String name;
        private String label;
        private String description;
        private List<StateYaml>      states      = List.of();
        private List<TransitionYaml> transitions = List.of();

        public static WorkflowYaml from(MetaWorkflow w) {
            var y = new WorkflowYaml();
            y.name = w.getName();
            y.label = w.getLabel();
            y.description = w.getDescription();
            y.states = w.getStates().stream()
                .sorted(Comparator.comparingInt(MetaState::getPosition).thenComparing(MetaState::getName))
                .map(StateYaml::from).toList();
            y.transitions = w.getTransitions().stream()
                .sorted(Comparator.comparing((MetaTransition t) -> t.getFromState().getName())
                    .thenComparing(t -> t.getToState().getName())
                    .thenComparing(MetaTransition::getLabel))
                .map(TransitionYaml::from).toList();
            return y;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class StateYaml {
        private String  name;
        private String  label;
        private boolean initial;
        private boolean terminal;
        private String  color;
        private int     position;

        public static StateYaml from(MetaState s) {
            var y = new StateYaml();
            y.name = s.getName();
            y.label = s.getLabel();
            y.initial = s.isInitial();
            y.terminal = s.isTerminal();
            y.color = s.getColor();
            y.position = s.getPosition();
            return y;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class TransitionYaml {
        private String   from;
        private String   to;
        private String   label;
        /** Declarative guard, written as a nested YAML object. */
        private JsonNode condition;
        /** Name of a {@code WorkflowAction} bean. */
        private String   action;
        private String   requiredRole;

        public static TransitionYaml from(MetaTransition t) {
            var y = new TransitionYaml();
            y.from = t.getFromState().getName();
            y.to = t.getToState().getName();
            y.label = t.getLabel();
            y.condition = MetaYamlCodec.parseJson(t.getCondition());
            y.action = t.getActionName();
            y.requiredRole = t.getRequiredRole();
            return y;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class EntityYaml {
        private String  name;
        private String  label;
        private String  description;
        private boolean softDelete = true;
        private boolean auditable  = true;
        /** Workflow name. */
        private String  workflow;
        private List<FieldYaml>      fields      = List.of();
        private List<PermissionYaml> permissions = List.of();
        private List<ViewYaml>       views       = List.of();

        public static EntityYaml from(MetaEntity e, List<MetaPermission> permissions) {
            var y = new EntityYaml();
            y.name = e.getName();
            y.label = e.getLabel();
            y.description = e.getDescription();
            y.softDelete = e.isSoftDelete();
            y.auditable = e.isAuditable();
            y.workflow = e.getWorkflow() != null ? e.getWorkflow().getName() : null;
            y.fields = e.getFields().stream()
                .sorted(Comparator.comparingInt(MetaField::getPosition).thenComparing(MetaField::getName))
                .map(FieldYaml::from).toList();
            y.permissions = permissions.stream()
                .sorted(Comparator.comparing(p -> p.getRole().getName()))
                .map(PermissionYaml::from).toList();
            y.views = e.getViews().stream()
                .sorted(Comparator.comparing(MetaView::getName))
                .map(ViewYaml::from).toList();
            return y;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class FieldYaml {
        private String   name;
        private String   label;
        private String   type;
        private boolean  required;
        private boolean  unique;
        private boolean  searchable = true;
        private String   defaultValue;
        private JsonNode options;
        private int      position;

        public static FieldYaml from(MetaField f) {
            var y = new FieldYaml();
            y.name = f.getName();
            y.label = f.getLabel();
            y.type = f.getType().name();
            y.required = f.isRequired();
            y.unique = f.isUnique();
            y.searchable = f.isSearchable();
            y.defaultValue = f.getDefaultValue();
            y.options = f.getOptions() != null && !f.getOptions().isEmpty() ? f.getOptions() : null;
            y.position = f.getPosition();
            return y;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class PermissionYaml {
        private String   role;
        private boolean  canRead;
        private boolean  canWrite;
        private boolean  canDelete;
        /** Declarative row filter, written as a nested YAML object. */
        private JsonNode rowFilter;
        /** {@code {hidden: [..], readonly: [..]}}. */
        private JsonNode fieldMask;

        public static PermissionYaml from(MetaPermission p) {
            var y = new PermissionYaml();
            y.role = p.getRole().getName();
            y.canRead = p.isCanRead();
            y.canWrite = p.isCanWrite();
            y.canDelete = p.isCanDelete();
            y.rowFilter = MetaYamlCodec.parseJson(p.getRowFilter());
            y.fieldMask = p.getFieldMask() != null && !p.getFieldMask().isEmpty() ? p.getFieldMask() : null;
            return y;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class ViewYaml {
        private String   name;
        private String   label;
        private String   type;
        private boolean  defaultView;
        private JsonNode config;

        public static ViewYaml from(MetaView v) {
            var y = new ViewYaml();
            y.name = v.getName();
            y.label = v.getLabel();
            y.type = v.getType().name();
            y.defaultView = v.isDefaultView();
            y.config = v.getConfig() != null && !v.getConfig().isEmpty() ? v.getConfig() : null;
            return y;
        }
    }
}
