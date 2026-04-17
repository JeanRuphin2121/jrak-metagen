package io.metagen.engine.workflow;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaTransition;
import io.metagen.core.domain.MetaWorkflow;
import io.metagen.core.repository.MetaEntityRepository;
import io.metagen.core.repository.MetaWorkflowRepository;
import io.metagen.engine.crud.DynamicCrudService;
import io.metagen.engine.crud.DynamicQueryBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Executes workflow state transitions for entity records.
 *
 * <h2>Transition execution steps</h2>
 * <ol>
 *   <li>Load the entity and its workflow (with all states + transitions)</li>
 *   <li>Load the current record — read its {@code state} field</li>
 *   <li>Find the requested transition by id</li>
 *   <li>Verify the transition's {@code fromState} matches the current record state</li>
 *   <li>Check {@code requiredRole} against the user's roles</li>
 *   <li>Evaluate {@code conditionScript} (Groovy) — must return {@code true}</li>
 *   <li>Persist the new state ({@code UPDATE ... SET state = :newState})</li>
 *   <li>Execute {@code actionScript} (Groovy) — side effects, notifications, etc.</li>
 *   <li>Return the updated record</li>
 * </ol>
 *
 * <h2>Script execution</h2>
 * <p>Groovy scripts receive a {@code record} binding (the current record map)
 * and a {@code context} binding (caller-supplied extra data).
 * Scripts run in a sandbox — Spring beans are not directly accessible from
 * scripts (use action hooks for service calls).</p>
 *
 * <p>In Phase 2 the scripting sandbox is minimal. A full Groovy sandbox with
 * class whitelisting will be added in Phase 3.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final MetaEntityRepository       metaEntityRepository;
    private final MetaWorkflowRepository     workflowRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final DynamicCrudService         crudService;
    private final DynamicQueryBuilder        queryBuilder;

    /**
     * Executes the requested transition.
     *
     * @param request all data needed to trigger the transition
     * @return the updated record with the new state
     * @throws NoSuchElementException if entity, record, or transition is not found
     * @throws IllegalStateException  if the transition cannot be executed
     *                                (wrong current state, role check, condition failed)
     */
    @Transactional
    public TransitionResult execute(TransitionRequest request) {
        // 1. Load entity + workflow
        MetaEntity entity = metaEntityRepository.findByNameWithAll(request.entityName())
            .orElseThrow(() -> new NoSuchElementException("Unknown entity: " + request.entityName()));

        MetaWorkflow workflow = entity.getWorkflow();
        if (workflow == null) {
            throw new IllegalStateException(
                "Entity '" + request.entityName() + "' has no workflow attached");
        }

        // Full load with transitions
        workflow = workflowRepository.findByIdWithAll(workflow.getId())
            .orElseThrow(() -> new NoSuchElementException("Workflow not found"));

        // 2. Load the current record (bypasses RBAC for internal engine use)
        Map<String, Object> record = loadRaw(entity, request.recordId());
        String currentState = (String) record.get("state");

        // 3. Find the requested transition
        MetaTransition transition = workflow.getTransitions().stream()
            .filter(t -> t.getId().equals(request.transitionId()))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException(
                "Transition " + request.transitionId() + " not found in workflow"));

        // 4. Verify fromState matches current record state
        String fromStateName = transition.getFromState().getName();
        if (!fromStateName.equals(currentState)) {
            throw new IllegalStateException(
                "Cannot execute transition '" + transition.getLabel() +
                "': record is in state '" + currentState +
                "' but transition requires '" + fromStateName + "'");
        }

        // 5. Role check
        String requiredRole = transition.getRequiredRole();
        if (requiredRole != null && !request.userRoles().contains(requiredRole)
                                 && !request.userRoles().contains("ADMIN")) {
            throw new IllegalStateException(
                "Role '" + requiredRole + "' is required to execute transition '" +
                transition.getLabel() + "'");
        }

        // 6. Evaluate condition script
        if (transition.getConditionScript() != null && !transition.getConditionScript().isBlank()) {
            boolean conditionMet = evaluateCondition(transition.getConditionScript(), record, request);
            if (!conditionMet) {
                throw new IllegalStateException(
                    "Transition '" + transition.getLabel() + "' condition not met");
            }
        }

        // 7. Persist new state
        String newState = transition.getToState().getName();
        var params = new MapSqlParameterSource()
            .addValue("newState",   newState)
            .addValue("updatedBy",  request.username())
            .addValue("id",         request.recordId());

        Map<String, Object> updated = jdbc.queryForMap(
            queryBuilder.buildStateUpdate(entity), params);

        log.info("Workflow transition: {}/{} {} → {} by {}",
                 request.entityName(), request.recordId(),
                 currentState, newState, request.username());

        // 8. Execute action script (async-safe, errors are logged not thrown)
        if (transition.getActionScript() != null && !transition.getActionScript().isBlank()) {
            runActionScript(transition.getActionScript(), updated, request);
        }

        return new TransitionResult(
            request.entityName(),
            request.recordId(),
            currentState,
            newState,
            transition.getLabel(),
            updated
        );
    }

    // ── Script execution ─────────────────────────────────────────────

    /**
     * Evaluates a Groovy condition script.
     *
     * <p>The script receives two bindings:</p>
     * <ul>
     *   <li>{@code record}  — the current record as a {@code Map<String, Object>}</li>
     *   <li>{@code context} — caller-supplied extra data as a {@code Map<String, Object>}</li>
     * </ul>
     * Must return a {@code Boolean}. Any exception or non-boolean is treated as {@code false}.
     */
    private boolean evaluateCondition(String script,
                                       Map<String, Object> record,
                                       TransitionRequest request) {
        try {
            var shell    = buildGroovyShell(record, request);
            Object result = shell.evaluate(script);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Condition script evaluation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Executes a Groovy action script.
     * Errors are caught and logged — action failures do not roll back the state change.
     */
    private void runActionScript(String script,
                                  Map<String, Object> record,
                                  TransitionRequest request) {
        try {
            var shell = buildGroovyShell(record, request);
            shell.evaluate(script);
        } catch (Exception e) {
            log.error("Action script failed for transition on {}/{}: {}",
                      request.entityName(), request.recordId(), e.getMessage());
        }
    }

    private groovy.lang.GroovyShell buildGroovyShell(Map<String, Object> record,
                                                       TransitionRequest request) {
        var binding = new groovy.lang.Binding();
        binding.setVariable("record",  record);
        binding.setVariable("context", request.context());
        return new groovy.lang.GroovyShell(binding);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Loads a record directly, bypassing RBAC (used internally by the engine). */
    private Map<String, Object> loadRaw(MetaEntity entity, UUID id) {
        String sql = queryBuilder.buildSelectById(entity);
        var params = new MapSqlParameterSource("id", id);
        var rows = jdbc.queryForList(sql, params);
        if (rows.isEmpty()) {
            throw new NoSuchElementException(
                entity.getName() + " with id=" + id + " not found");
        }
        return rows.getFirst();
    }
}
