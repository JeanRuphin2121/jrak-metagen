package io.metagen.engine.workflow;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaState;
import io.metagen.core.domain.MetaTransition;
import io.metagen.core.domain.MetaWorkflow;
import io.metagen.engine.crud.DynamicCrudService;
import io.metagen.engine.crud.DynamicQueryBuilder;
import io.metagen.engine.expr.Condition;
import io.metagen.engine.meta.EntityFieldResolver;
import io.metagen.engine.meta.MetaModelService;
import io.metagen.engine.rbac.Access;
import io.metagen.engine.rbac.RbacEnforcer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Executes workflow transitions on records.
 *
 * <p>A transition goes through, in order:
 * <ol>
 *   <li>visibility — the record must be readable by the user (row filters apply);</li>
 *   <li>state — the transition must start from the record's current state;</li>
 *   <li>authorisation — the transition's required role, or WRITE access on the
 *       entity when no role is required;</li>
 *   <li>guard — the declarative {@link Condition}, evaluated on the record;</li>
 *   <li>a compare-and-set state update (safe under concurrency);</li>
 *   <li>a history entry, then the optional {@link WorkflowAction}.</li>
 * </ol>
 * Everything runs in one transaction: a failing action cancels the transition.
 * A guard that cannot be evaluated is an error, never a silent "false".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final MetaModelService           metaModel;
    private final NamedParameterJdbcTemplate jdbc;
    private final DynamicCrudService         crud;
    private final DynamicQueryBuilder        queryBuilder;
    private final RbacEnforcer               rbac;
    private final WorkflowActionRegistry     actions;

    @Transactional
    public TransitionResult execute(TransitionRequest request) {
        MetaEntity   entity   = metaModel.entity(request.entityName());
        MetaWorkflow workflow = requireWorkflow(entity);
        rbac.assertAllowed(request.userRoles(), entity, Access.READ);

        Map<String, Object> record = crud.loadRaw(
            entity, request.recordId(), request.userRoles(), request.username(), Access.READ);
        String storedState  = (String) record.get("state");
        String currentState = effectiveState(workflow, storedState);

        MetaTransition transition = workflow.getTransitions().stream()
            .filter(t -> t.getId().equals(request.transitionId()))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException(
                "Transition " + request.transitionId() + " not found in workflow '"
                + workflow.getName() + "'"));

        if (!transition.getFromState().getName().equals(currentState)) {
            throw new IllegalStateException(
                "Cannot apply '" + transition.getLabel() + "': the record is in state '"
                + currentState + "', not '" + transition.getFromState().getName() + "'");
        }
        if (!isAuthorised(transition, entity, request.userRoles())) {
            throw new SecurityException(
                "You are not allowed to apply '" + transition.getLabel() + "'");
        }
        Condition guard = Condition.parse(transition.getCondition());
        if (guard != null && !guard.test(record, new EntityFieldResolver(entity),
                                         DynamicCrudService.variables(request.username()))) {
            throw new IllegalStateException(
                "The conditions for '" + transition.getLabel() + "' are not met");
        }
        actions.requireKnown(transition.getActionName());

        String newState = transition.getToState().getName();
        List<Map<String, Object>> updated = jdbc.queryForList(
            queryBuilder.buildStateUpdate(entity),
            new MapSqlParameterSource()
                .addValue("newState", newState)
                .addValue("expectedState", storedState)
                .addValue("updatedBy", entity.isAuditable() ? request.username() : null)
                .addValue("id", request.recordId()));
        if (updated.isEmpty()) {
            throw new IllegalStateException(
                "The record was modified by someone else — reload it and retry");
        }

        jdbc.update("""
            INSERT INTO workflow_history
                (entity_name, record_id, from_state, to_state, transition_label, username, comment)
            VALUES (:entity, :recordId, :fromState, :toState, :label, :username, :comment)
            """, new MapSqlParameterSource()
                .addValue("entity", entity.getName())
                .addValue("recordId", request.recordId())
                .addValue("fromState", currentState)
                .addValue("toState", newState)
                .addValue("label", transition.getLabel())
                .addValue("username", request.username())
                .addValue("comment", blankToNull(request.comment())));

        Map<String, Object> stored = updated.get(0);
        if (transition.getActionName() != null && !transition.getActionName().isBlank()) {
            actions.find(transition.getActionName()).orElseThrow().execute(new TransitionContext(
                entity, request.recordId(), stored, currentState, newState,
                transition.getLabel(), request.username(), request.comment()));
        }

        log.info("Workflow transition: {}/{} {} -> {} by {}",
                 entity.getName(), request.recordId(), currentState, newState, request.username());

        return new TransitionResult(entity.getName(), request.recordId(), currentState, newState,
            transition.getLabel(),
            rbac.applyFieldMask(crud.toApi(stored), request.userRoles(), entity));
    }

    /** Transitions the user could trigger now: right state, right role, guard satisfied. */
    @Transactional(readOnly = true)
    public List<AvailableTransition> available(String entityName, UUID recordId,
                                               List<String> roles, String username) {
        MetaEntity entity = metaModel.entity(entityName);
        if (entity.getWorkflow() == null) return List.of();
        rbac.assertAllowed(roles, entity, Access.READ);

        Map<String, Object> record = crud.loadRaw(entity, recordId, roles, username, Access.READ);
        String currentState = effectiveState(entity.getWorkflow(), (String) record.get("state"));
        var resolver  = new EntityFieldResolver(entity);
        var variables = DynamicCrudService.variables(username);

        return entity.getWorkflow().getTransitions().stream()
            .filter(t -> t.getFromState().getName().equals(currentState))
            .filter(t -> isAuthorised(t, entity, roles))
            .filter(t -> {
                Condition guard = Condition.parse(t.getCondition());
                return guard == null || guard.test(record, resolver, variables);
            })
            .sorted((a, b) -> Integer.compare(a.getToState().getPosition(), b.getToState().getPosition()))
            .map(t -> new AvailableTransition(t.getId(), t.getLabel(),
                                              t.getToState().getName(), t.getToState().getLabel()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(String entityName, UUID recordId,
                                             List<String> roles, String username) {
        MetaEntity entity = metaModel.entity(entityName);
        rbac.assertAllowed(roles, entity, Access.READ);
        crud.loadRaw(entity, recordId, roles, username, Access.READ);   // visibility check
        return jdbc.queryForList("""
            SELECT from_state, to_state, transition_label, username, comment, created_at
            FROM workflow_history
            WHERE entity_name = :entity AND record_id = :recordId
            ORDER BY created_at, id
            """, new MapSqlParameterSource("entity", entity.getName()).addValue("recordId", recordId));
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private MetaWorkflow requireWorkflow(MetaEntity entity) {
        if (entity.getWorkflow() == null) {
            throw new IllegalStateException("Entity '" + entity.getName() + "' has no workflow");
        }
        return entity.getWorkflow();
    }

    /** Records created before a workflow was attached have no state: treat them as initial. */
    private String effectiveState(MetaWorkflow workflow, String stored) {
        if (stored != null) return stored;
        return workflow.getInitialState().map(MetaState::getName).orElse(null);
    }

    private boolean isAuthorised(MetaTransition transition, MetaEntity entity, List<String> roles) {
        if (rbac.isAdmin(roles)) return true;
        String required = transition.getRequiredRole();
        if (required != null && !required.isBlank()) return roles.contains(required);
        return rbac.isAllowed(roles, entity, Access.WRITE);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
