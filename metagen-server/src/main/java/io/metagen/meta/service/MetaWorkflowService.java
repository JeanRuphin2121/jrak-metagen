package io.metagen.meta.service;

import io.metagen.core.domain.*;
import io.metagen.core.repository.MetaEntityRepository;
import io.metagen.core.repository.MetaWorkflowRepository;
import io.metagen.meta.dto.CreateWorkflowRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Business logic for managing workflow metadata in the Studio. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaWorkflowService {

    private final MetaWorkflowRepository workflowRepository;
    private final MetaEntityRepository   entityRepository;

    @Transactional(readOnly = true)
    public List<MetaWorkflow> listAll() {
        return workflowRepository.findAll();
    }

    @Transactional(readOnly = true)
    public MetaWorkflow getByName(String name) {
        return workflowRepository.findByName(name)
            .orElseThrow(() -> new NoSuchElementException("Workflow not found: " + name));
    }

    @Transactional(readOnly = true)
    public MetaWorkflow getById(UUID id) {
        return workflowRepository.findByIdWithAll(id)
            .orElseThrow(() -> new NoSuchElementException("Workflow not found: " + id));
    }

    /**
     * Creates a workflow with all its states and transitions in one transaction.
     *
     * <h2>Validation</h2>
     * <ul>
     *   <li>Exactly one state must have {@code initial = true}</li>
     *   <li>All transition fromState/toState must reference declared state names</li>
     * </ul>
     */
    @Transactional
    public MetaWorkflow create(CreateWorkflowRequest req) {
        if (workflowRepository.existsByName(req.name())) {
            throw new IllegalArgumentException("Workflow '" + req.name() + "' already exists");
        }
        validateStates(req);

        MetaWorkflow workflow = MetaWorkflow.builder()
            .name(req.name())
            .label(req.label())
            .description(req.description())
            .build();

        // Build states first — transitions reference them
        Map<String, MetaState> statesByName = req.states().stream()
            .map(dto -> {
                var state = MetaState.builder()
                    .name(dto.name())
                    .label(dto.label())
                    .initial(dto.initial())
                    .terminal(dto.terminal())
                    .color(dto.color())
                    .position(dto.position())
                    .build();
                workflow.addState(state);
                return state;
            })
            .collect(Collectors.toMap(MetaState::getName, Function.identity()));

        // Save now so states get IDs before transitions reference them
        MetaWorkflow saved = workflowRepository.save(workflow);

        // Reload with IDs to wire transitions
        Map<String, MetaState> persistedStates = saved.getStates().stream()
            .collect(Collectors.toMap(MetaState::getName, Function.identity()));

        if (req.transitions() != null) {
            for (var dto : req.transitions()) {
                MetaState from = persistedStates.get(dto.fromState());
                MetaState to   = persistedStates.get(dto.toState());
                if (from == null || to == null) {
                    throw new IllegalArgumentException(
                        "Transition references unknown state: " + dto.fromState() + " → " + dto.toState());
                }
                var transition = MetaTransition.builder()
                    .fromState(from)
                    .toState(to)
                    .label(dto.label())
                    .conditionScript(dto.conditionScript())
                    .actionScript(dto.actionScript())
                    .requiredRole(dto.requiredRole())
                    .build();
                saved.addTransition(transition);
            }
            saved = workflowRepository.save(saved);
        }

        log.info("Created workflow '{}' with {} states, {} transitions",
                 saved.getName(),
                 saved.getStates().size(),
                 saved.getTransitions().size());
        return saved;
    }

    /**
     * Attaches a workflow to an entity.
     * Records already in progress keep their current state — no migration needed.
     */
    @Transactional
    public void attachToEntity(String workflowName, String entityName) {
        MetaWorkflow workflow = getByName(workflowName);
        MetaEntity   entity   = entityRepository.findByName(entityName)
            .orElseThrow(() -> new NoSuchElementException("Entity not found: " + entityName));

        entity.setWorkflow(workflow);
        entityRepository.save(entity);
        log.info("Attached workflow '{}' to entity '{}'", workflowName, entityName);
    }

    @Transactional
    public void delete(String name) {
        MetaWorkflow workflow = getByName(name);
        workflowRepository.delete(workflow);
        log.info("Deleted workflow '{}'", name);
    }

    // ── Validation ────────────────────────────────────────────────────

    private void validateStates(CreateWorkflowRequest req) {
        if (req.states() == null || req.states().isEmpty()) {
            throw new IllegalArgumentException("A workflow must have at least one state");
        }
        long initialCount = req.states().stream().filter(CreateWorkflowRequest.StateDto::initial).count();
        if (initialCount != 1) {
            throw new IllegalArgumentException(
                "A workflow must have exactly one initial state (found " + initialCount + ")");
        }
    }
}
