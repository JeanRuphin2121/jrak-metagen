package io.metagen.engine.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaRole;
import io.metagen.core.repository.MetaEntityRepository;
import io.metagen.core.repository.MetaPermissionRepository;
import io.metagen.core.repository.MetaRoleRepository;
import io.metagen.core.repository.MetaWorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Exports the configuration as YAML.
 *
 * <p>Output is deterministic (everything sorted by name or position) so two
 * exports of the same configuration are byte-identical and {@code git diff}
 * shows only real changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaYamlExporter {

    private final MetaEntityRepository     entityRepository;
    private final MetaWorkflowRepository   workflowRepository;
    private final MetaRoleRepository       roleRepository;
    private final MetaPermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public String exportAll() {
        return write(buildDto());
    }

    @Transactional(readOnly = true)
    public MetaYamlDto buildDto() {
        var dto = new MetaYamlDto();
        dto.setRoles(roleRepository.findByBuiltInFalse().stream()
            .sorted(Comparator.comparing(MetaRole::getName))
            .map(MetaYamlDto.RoleYaml::from).toList());
        dto.setWorkflows(workflowRepository.findAll().stream()
            .sorted(Comparator.comparing(w -> w.getName()))
            .map(w -> workflowRepository.findByIdWithAll(w.getId()).orElseThrow())
            .map(MetaYamlDto.WorkflowYaml::from).toList());
        dto.setEntities(entityRepository.findAll().stream()
            .sorted(Comparator.comparing(MetaEntity::getName))
            .map(e -> entityDto(e.getName())).toList());
        log.debug("Exported {} roles, {} workflows, {} entities",
                  dto.getRoles().size(), dto.getWorkflows().size(), dto.getEntities().size());
        return dto;
    }

    @Transactional(readOnly = true)
    public String exportEntity(String entityName) {
        return write(entityDto(entityName));
    }

    private MetaYamlDto.EntityYaml entityDto(String name) {
        MetaEntity entity = entityRepository.findByNameWithAll(name)
            .orElseThrow(() -> new NoSuchElementException("Entity not found: " + name));
        // Views are lazy: initialise inside the transaction.
        entity.getViews().size();
        return MetaYamlDto.EntityYaml.from(entity, permissionRepository.findByEntityId(entity.getId()));
    }

    private String write(Object value) {
        try {
            return MetaYamlCodec.YAML.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise configuration to YAML", e);
        }
    }
}
