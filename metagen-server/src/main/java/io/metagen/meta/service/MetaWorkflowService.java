package io.metagen.meta.service;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaTransition;
import io.metagen.core.domain.MetaWorkflow;
import io.metagen.core.repository.MetaEntityRepository;
import io.metagen.core.repository.MetaWorkflowRepository;
import io.metagen.engine.config.MetaYamlDto;
import io.metagen.engine.config.MetaYamlImporter;
import io.metagen.engine.expr.Condition;
import io.metagen.engine.meta.EntityFieldResolver;
import io.metagen.engine.meta.MetaModelService;
import io.metagen.meta.dto.CreateWorkflowRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Studio-side management of workflows. Creation and update share the YAML
 * importer's code path, so a workflow defined in the Studio is validated
 * exactly like one defined in a configuration file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaWorkflowService {

    private final MetaWorkflowRepository workflowRepository;
    private final MetaEntityRepository   entityRepository;
    private final MetaYamlImporter       importer;
    private final MetaModelService       metaModel;

    @Transactional(readOnly = true)
    public List<MetaWorkflow> listAll() {
        return workflowRepository.findAll().stream()
            .sorted((a, b) -> a.getName().compareTo(b.getName()))
            .map(w -> getById(w.getId()))
            .toList();
    }

    @Transactional(readOnly = true)
    public MetaWorkflow getByName(String name) {
        MetaWorkflow w = workflowRepository.findByName(name)
            .orElseThrow(() -> new NoSuchElementException("Workflow not found: " + name));
        return getById(w.getId());
    }

    @Transactional(readOnly = true)
    public MetaWorkflow getById(UUID id) {
        return workflowRepository.findByIdWithAll(id)
            .orElseThrow(() -> new NoSuchElementException("Workflow not found: " + id));
    }

    @Transactional
    public MetaWorkflow create(CreateWorkflowRequest req) {
        if (workflowRepository.existsByName(req.name())) {
            throw new IllegalArgumentException("Workflow '" + req.name() + "' already exists");
        }
        return upsert(req);
    }

    /** Creates the workflow or updates it in place (states and transitions are merged, never deleted). */
    @Transactional
    public MetaWorkflow upsert(CreateWorkflowRequest req) {
        var dto = new MetaYamlDto();
        dto.setWorkflows(List.of(toYaml(req)));
        importer.importDto(dto, false);
        log.info("Saved workflow '{}'", req.name());
        return getByName(req.name());
    }

    @Transactional
    public void attachToEntity(String workflowName, String entityName) {
        MetaWorkflow workflow = getByName(workflowName);
        MetaEntity   entity   = entityRepository.findByNameWithAll(entityName)
            .orElseThrow(() -> new NoSuchElementException("Entity not found: " + entityName));

        var resolver = new EntityFieldResolver(entity);
        for (MetaTransition t : workflow.getTransitions()) {
            Condition guard = Condition.parse(t.getCondition());
            if (guard != null) {
                try {
                    guard.validate(resolver);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Transition '" + t.getLabel()
                        + "' cannot be used on '" + entityName + "': " + e.getMessage());
                }
            }
        }
        entity.setWorkflow(workflow);
        entityRepository.save(entity);
        metaModel.evictAll();
        log.info("Attached workflow '{}' to entity '{}'", workflowName, entityName);
    }

    @Transactional
    public void delete(String name) {
        MetaWorkflow workflow = getByName(name);
        if (entityRepository.existsByWorkflowId(workflow.getId())) {
            throw new IllegalStateException(
                "Workflow '" + name + "' is attached to at least one entity — detach it first");
        }
        workflowRepository.delete(workflow);
        metaModel.evictAll();
        log.info("Deleted workflow '{}'", name);
    }

    private MetaYamlDto.WorkflowYaml toYaml(CreateWorkflowRequest req) {
        var y = new MetaYamlDto.WorkflowYaml();
        y.setName(req.name());
        y.setLabel(req.label());
        y.setDescription(req.description());
        y.setStates(req.states().stream().map(s -> {
            var sy = new MetaYamlDto.StateYaml();
            sy.setName(s.name());
            sy.setLabel(s.label());
            sy.setInitial(s.initial());
            sy.setTerminal(s.terminal());
            sy.setColor(s.color());
            sy.setPosition(s.position());
            return sy;
        }).toList());
        y.setTransitions(req.transitions().stream().map(t -> {
            var ty = new MetaYamlDto.TransitionYaml();
            ty.setFrom(t.fromState());
            ty.setTo(t.toState());
            ty.setLabel(t.label());
            ty.setCondition(t.condition());
            ty.setAction(t.action());
            ty.setRequiredRole(t.requiredRole());
            return ty;
        }).toList());
        return y;
    }
}
