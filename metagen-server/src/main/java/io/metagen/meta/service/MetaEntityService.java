package io.metagen.meta.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.core.domain.MetaFieldType;
import io.metagen.core.domain.MetaWorkflow;
import io.metagen.core.repository.MetaEntityRepository;
import io.metagen.core.repository.MetaFieldRepository;
import io.metagen.core.repository.MetaWorkflowRepository;
import io.metagen.engine.crud.ValueCoercer;
import io.metagen.engine.deploy.EntityDeployer;
import io.metagen.engine.meta.MetaModelService;
import io.metagen.engine.meta.NamingRules;
import io.metagen.meta.dto.CreateEntityRequest;
import io.metagen.meta.dto.CreateFieldRequest;
import io.metagen.meta.dto.UpdateFieldRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Studio-side management of entity definitions (metadata only — tables are
 * created or altered by {@link #deploy}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaEntityService {

    private final MetaEntityRepository   metaEntityRepository;
    private final MetaFieldRepository    metaFieldRepository;
    private final MetaWorkflowRepository workflowRepository;
    private final EntityDeployer         deployer;
    private final MetaModelService       metaModel;

    @Transactional(readOnly = true)
    public List<MetaEntity> listAll() {
        return metaEntityRepository.findAll().stream()
            .sorted((a, b) -> a.getName().compareTo(b.getName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public MetaEntity getByName(String name) {
        return metaEntityRepository.findByNameWithAll(name)
            .orElseThrow(() -> new NoSuchElementException("Entity not found: " + name));
    }

    @Transactional
    public MetaEntity create(CreateEntityRequest req) {
        NamingRules.entityName(req.name()).ifPresent(m -> { throw new IllegalArgumentException(m); });
        if (metaEntityRepository.existsByName(req.name())) {
            throw new IllegalArgumentException("Entity '" + req.name() + "' already exists");
        }
        var entity = MetaEntity.builder()
            .name(req.name())
            .label(req.label())
            .tableName(MetaEntity.defaultTableName(req.name()))
            .description(req.description())
            .softDelete(req.softDelete())
            .auditable(req.auditable())
            .build();

        if (req.workflow() != null && !req.workflow().isBlank()) {
            entity.setWorkflow(workflowRepository.findByName(req.workflow())
                .orElseThrow(() -> new IllegalArgumentException("Unknown workflow: " + req.workflow())));
        }
        Set<String> names = new HashSet<>();
        for (int i = 0; i < req.fields().size(); i++) {
            CreateFieldRequest f = req.fields().get(i);
            if (!names.add(f.name())) {
                throw new IllegalArgumentException("Field '" + f.name() + "' is defined twice");
            }
            validateField(f.name(), f.type(), f.options());
            entity.addField(buildField(f, entity, f.position() != null ? f.position() : i));
        }
        MetaEntity saved = metaEntityRepository.save(entity);
        metaModel.evictAll();
        log.info("Created entity metadata: '{}'", saved.getName());
        return saved;
    }

    @Transactional
    public MetaEntity update(String name, CreateEntityRequest req) {
        MetaEntity entity = getByName(name);
        entity.setLabel(req.label());
        entity.setDescription(req.description());
        entity.setSoftDelete(req.softDelete());
        entity.setAuditable(req.auditable());
        MetaEntity saved = metaEntityRepository.save(entity);
        metaModel.evictAll();
        log.info("Updated entity metadata: '{}'", name);
        return saved;
    }

    @Transactional
    public void delete(String name) {
        MetaEntity entity = getByName(name);
        metaEntityRepository.delete(entity);
        metaModel.evictAll();
        log.info("Deleted entity metadata: '{}' (data table preserved)", name);
    }

    @Transactional
    public MetaField addField(String entityName, CreateFieldRequest req) {
        MetaEntity entity = getByName(entityName);
        if (metaFieldRepository.existsByEntityIdAndName(entity.getId(), req.name())) {
            throw new IllegalArgumentException(
                "Field '" + req.name() + "' already exists on entity '" + entityName + "'");
        }
        validateField(req.name(), req.type(), req.options());
        int position = req.position() != null ? req.position()
            : entity.getFields().stream().mapToInt(MetaField::getPosition).max().orElse(-1) + 1;
        MetaField field = buildField(req, entity, position);
        entity.addField(field);
        metaEntityRepository.save(entity);
        metaModel.evictAll();
        return field;
    }

    @Transactional
    public MetaField updateField(String entityName, UUID fieldId, UpdateFieldRequest req) {
        MetaEntity entity = getByName(entityName);
        MetaField field = entity.getFields().stream()
            .filter(f -> f.getId().equals(fieldId)).findFirst()
            .orElseThrow(() -> new NoSuchElementException("Field " + fieldId + " not found on '" + entityName + "'"));
        validateField(field.getName(), field.getType(), req.options());
        field.setLabel(req.label());
        field.setRequired(req.required());
        field.setUnique(req.unique());
        field.setSearchable(req.searchable());
        field.setDefaultValue(req.defaultValue());
        field.setOptions(req.options());
        if (req.position() != null) field.setPosition(req.position());
        metaEntityRepository.save(entity);
        metaModel.evictAll();
        return field;
    }

    @Transactional
    public void deleteField(String entityName, UUID fieldId) {
        MetaEntity entity = getByName(entityName);
        MetaField field = entity.getFields().stream()
            .filter(f -> f.getId().equals(fieldId)).findFirst()
            .orElseThrow(() -> new NoSuchElementException(
                "Field " + fieldId + " not found on entity '" + entityName + "'"));
        entity.removeField(field);
        metaEntityRepository.save(entity);
        metaModel.evictAll();
    }

    @Transactional
    public EntityDeployer.DeployResult deploy(String entityName) {
        MetaEntity entity = getByName(entityName);
        EntityDeployer.DeployResult result = deployer.deploy(entity);
        log.info("Deploy result: {}", result.summary());
        return result;
    }

    /** Deploys every entity; foreign keys are resolved in a second pass. */
    @Transactional
    public List<EntityDeployer.DeployResult> deployAll() {
        List<MetaEntity> all = metaEntityRepository.findAll().stream()
            .map(e -> getByName(e.getName()))
            .sorted((a, b) -> a.getName().compareTo(b.getName()))
            .toList();
        return deployer.deployAll(all);
    }

    // ── Validation ────────────────────────────────────────────────────────

    /** Rejects field definitions the engine could not store or serve correctly. */
    private void validateField(String name, MetaFieldType type, JsonNode options) {
        NamingRules.fieldName(name).ifPresent(m -> { throw new IllegalArgumentException(m); });
        if (type == MetaFieldType.ENUM || type == MetaFieldType.MULTI_ENUM) {
            if (ValueCoercer.allowedValues(options).isEmpty()) {
                throw new IllegalArgumentException(
                    "Field '" + name + "' (" + type + ") needs options.values, e.g. {\"values\": [\"A\", \"B\"]}");
            }
        }
        if (type == MetaFieldType.REFERENCE) {
            JsonNode target = options != null ? options.get("targetEntity") : null;
            if (target == null || target.asText().isBlank()) {
                throw new IllegalArgumentException(
                    "Field '" + name + "' (REFERENCE) needs options.targetEntity");
            }
            if (!metaEntityRepository.existsByName(target.asText())) {
                throw new IllegalArgumentException(
                    "Field '" + name + "': target entity '" + target.asText() + "' does not exist");
            }
        }
    }

    private MetaField buildField(CreateFieldRequest req, MetaEntity entity, int position) {
        return MetaField.builder()
            .entity(entity)
            .name(req.name())
            .label(req.label())
            .type(req.type())
            .required(req.required())
            .unique(req.unique())
            .searchable(req.searchable())
            .defaultValue(req.defaultValue())
            .options(req.options())
            .position(position)
            .build();
    }
}
