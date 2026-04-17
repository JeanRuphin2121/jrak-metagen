package io.metagen.meta.service;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.core.repository.MetaEntityRepository;
import io.metagen.core.repository.MetaFieldRepository;
import io.metagen.engine.crud.DynamicCrudService;
import io.metagen.engine.deploy.EntityDeployer;
import io.metagen.meta.dto.CreateEntityRequest;
import io.metagen.meta.dto.CreateFieldRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Business logic for the Studio API — manages entity and field metadata.
 *
 * <p>This service is the entry point for all configuration changes made through
 * the Studio UI or the Studio REST API. It orchestrates:</p>
 * <ul>
 *   <li>Persisting metadata changes to the {@code meta_*} tables</li>
 *   <li>Invalidating the engine's metadata cache after each change</li>
 *   <li>Delegating table creation/migration to {@link EntityDeployer}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaEntityService {

    private final MetaEntityRepository metaEntityRepository;
    private final MetaFieldRepository  metaFieldRepository;
    private final EntityDeployer       deployer;
    private final DynamicCrudService   crudService;

    // ── LIST ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MetaEntity> listAll() {
        return metaEntityRepository.findAll();
    }

    // ── GET ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MetaEntity getByName(String name) {
        return metaEntityRepository.findByNameWithFields(name)
            .orElseThrow(() -> new NoSuchElementException("Entity not found: " + name));
    }

    // ── CREATE ───────────────────────────────────────────────────────

    /**
     * Creates a new entity definition (metadata only — does not deploy the table yet).
     * Call {@link #deploy(String)} separately to create the {@code data_*} table.
     */
    @Transactional
    public MetaEntity create(CreateEntityRequest req) {
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

        // Add initial fields
        for (int i = 0; i < req.fields().size(); i++) {
            entity.addField(buildField(req.fields().get(i), entity, i));
        }

        MetaEntity saved = metaEntityRepository.save(entity);
        log.info("Created entity metadata: '{}'", saved.getName());
        return saved;
    }

    // ── UPDATE ───────────────────────────────────────────────────────

    /**
     * Updates entity-level properties (label, description, softDelete, auditable).
     * Does not update fields — use {@link #addField} / {@link #deleteField}.
     */
    @Transactional
    public MetaEntity update(String name, CreateEntityRequest req) {
        MetaEntity entity = getByName(name);

        entity.setLabel(req.label());
        entity.setDescription(req.description());
        entity.setSoftDelete(req.softDelete());
        entity.setAuditable(req.auditable());

        MetaEntity saved = metaEntityRepository.save(entity);
        crudService.evictCache(name);
        log.info("Updated entity metadata: '{}'", name);
        return saved;
    }

    // ── DELETE ───────────────────────────────────────────────────────

    /**
     * Deletes the entity metadata.
     * WARNING: does NOT drop the {@code data_*} table — data is preserved.
     * Use a separate migration script to drop the table if needed.
     */
    @Transactional
    public void delete(String name) {
        MetaEntity entity = getByName(name);
        metaEntityRepository.delete(entity);
        crudService.evictCache(name);
        log.info("Deleted entity metadata: '{}' (data table preserved)", name);
    }

    // ── FIELDS ───────────────────────────────────────────────────────

    @Transactional
    public MetaField addField(String entityName, CreateFieldRequest req) {
        MetaEntity entity = getByName(entityName);

        if (metaFieldRepository.existsByEntityIdAndName(entity.getId(), req.name())) {
            throw new IllegalArgumentException(
                "Field '" + req.name() + "' already exists on entity '" + entityName + "'");
        }

        MetaField field = buildField(req, entity, req.position());
        entity.addField(field);
        metaEntityRepository.save(entity);
        crudService.evictCache(entityName);
        return field;
    }

    @Transactional
    public void deleteField(String entityName, UUID fieldId) {
        MetaEntity entity = getByName(entityName);
        MetaField  field  = metaFieldRepository.findById(fieldId)
            .orElseThrow(() -> new NoSuchElementException("Field " + fieldId + " not found"));

        entity.removeField(field);
        metaEntityRepository.save(entity);
        crudService.evictCache(entityName);
        // NOTE: The column is NOT dropped from the data table — safe migration rule.
    }

    // ── DEPLOY ───────────────────────────────────────────────────────

    /**
     * Deploys (or migrates) the {@code data_*} table for the given entity.
     * Safe to call multiple times — idempotent.
     *
     * @return human-readable summary of what was done
     */
    @Transactional
    public EntityDeployer.DeployResult deploy(String entityName) {
        MetaEntity entity = metaEntityRepository.findByNameWithAll(entityName)
            .orElseThrow(() -> new NoSuchElementException("Entity not found: " + entityName));
        EntityDeployer.DeployResult result = deployer.deploy(entity);
        log.info("Deploy result: {}", result.summary());
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────

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
