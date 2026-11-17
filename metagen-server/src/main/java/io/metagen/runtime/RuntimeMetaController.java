package io.metagen.runtime;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.core.domain.MetaFieldType;
import io.metagen.core.domain.MetaState;
import io.metagen.core.repository.MetaEntityRepository;
import io.metagen.engine.api.DynamicRestController;
import io.metagen.engine.meta.MetaModelService;
import io.metagen.engine.rbac.EntityAccess;
import io.metagen.engine.rbac.RbacEnforcer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * What the generated UI needs to know, filtered by the caller's roles: which
 * entities exist, their fields (minus hidden ones), the workflow states and
 * what the user may do. Available to any authenticated user — it never
 * exposes entities the user cannot read.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Runtime API", description = "Metadata for the generated UI")
public class RuntimeMetaController {

    private final MetaEntityRepository entityRepository;
    private final MetaModelService     metaModel;
    private final RbacEnforcer         rbac;

    @GetMapping("/api/me")
    @Operation(summary = "Current user")
    public Map<String, Object> me(Authentication auth) {
        List<String> roles = DynamicRestController.roles(auth);
        return Map.of("username", auth.getName(), "roles", roles, "admin", rbac.isAdmin(roles));
    }

    @GetMapping("/api/meta/entities")
    @Transactional(readOnly = true)
    @Operation(summary = "Entities the current user can read, with their fields and permissions")
    public List<EntityMeta> entities(Authentication auth) {
        List<String> roles = DynamicRestController.roles(auth);
        return entityRepository.findAll().stream()
            .sorted(Comparator.comparing(MetaEntity::getName))
            .map(e -> metaModel.entity(e.getName()))
            .filter(e -> rbac.isAllowed(roles, e, io.metagen.engine.rbac.Access.READ))
            .map(e -> EntityMeta.from(e, rbac.describe(roles, e)))
            .toList();
    }

    @GetMapping("/api/meta/entities/{name}")
    @Transactional(readOnly = true)
    @Operation(summary = "One entity's metadata for the current user")
    public EntityMeta entity(@PathVariable String name, Authentication auth) {
        List<String> roles = DynamicRestController.roles(auth);
        MetaEntity e = metaModel.entity(name);
        if (!rbac.isAllowed(roles, e, io.metagen.engine.rbac.Access.READ)) {
            // Same answer as for an unknown entity: do not reveal what exists.
            throw new NoSuchElementException("Entity not found: " + name);
        }
        return EntityMeta.from(e, rbac.describe(roles, e));
    }

    public record EntityMeta(String name, String label, String description,
                             boolean hasWorkflow, List<StateMeta> states,
                             List<FieldMeta> fields, EntityAccess access) {

        public record StateMeta(String name, String label, String color, boolean initial, boolean terminal) {}

        public record FieldMeta(String name, String label, MetaFieldType type, boolean required,
                                boolean unique, boolean searchable, boolean readonly,
                                String defaultValue, JsonNode options) {}

        static EntityMeta from(MetaEntity e, EntityAccess access) {
            List<StateMeta> states = e.getWorkflow() == null ? List.of()
                : e.getWorkflow().getStates().stream()
                    .sorted(Comparator.comparingInt(MetaState::getPosition))
                    .map(s -> new StateMeta(s.getName(), s.getLabel(), s.getColor(), s.isInitial(), s.isTerminal()))
                    .toList();
            List<FieldMeta> fields = e.getFields().stream()
                .filter(f -> !access.hiddenFields().contains(f.getName()))
                .sorted(Comparator.comparingInt(MetaField::getPosition))
                .map(f -> new FieldMeta(f.getName(), f.getLabel(), f.getType(), f.isRequired(), f.isUnique(),
                    f.isSearchable(), access.readonlyFields().contains(f.getName()),
                    f.getDefaultValue(), f.getOptions()))
                .toList();
            return new EntityMeta(e.getName(), e.getLabel(), e.getDescription(),
                e.getWorkflow() != null, states, fields, access);
        }
    }
}
