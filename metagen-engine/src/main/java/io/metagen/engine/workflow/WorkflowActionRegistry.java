package io.metagen.engine.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Index of the {@link WorkflowAction} beans available to workflow definitions. */
@Slf4j
@Component
public class WorkflowActionRegistry {

    private final Map<String, WorkflowAction> actions = new TreeMap<>();

    public WorkflowActionRegistry(List<WorkflowAction> beans) {
        for (WorkflowAction action : beans) {
            WorkflowAction previous = actions.put(action.name(), action);
            if (previous != null) {
                throw new IllegalStateException("Two workflow actions share the name '"
                    + action.name() + "': " + previous.getClass().getName()
                    + " and " + action.getClass().getName());
            }
        }
        log.info("Workflow actions available: {}", actions.keySet());
    }

    public Optional<WorkflowAction> find(String name) {
        return Optional.ofNullable(actions.get(name));
    }

    public Set<String> names() {
        return actions.keySet();
    }

    /** Fails with a clear message when metadata references an action that does not exist. */
    public void requireKnown(String name) {
        if (name != null && !name.isBlank() && !actions.containsKey(name)) {
            throw new IllegalArgumentException(
                "Unknown workflow action '" + name + "' (available: " + actions.keySet() + ")");
        }
    }
}
