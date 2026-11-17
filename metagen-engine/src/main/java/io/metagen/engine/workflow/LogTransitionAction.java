package io.metagen.engine.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Built-in example action: writes a business-level log line for the transition.
 * Use it as a template for your own actions.
 */
@Slf4j
@Component
public class LogTransitionAction implements WorkflowAction {

    @Override
    public String name() {
        return "log_transition";
    }

    @Override
    public void execute(TransitionContext ctx) {
        log.info("[workflow] {} {} : {} -> {} ({}) by {}",
                 ctx.entity().getName(), ctx.recordId(),
                 ctx.fromState(), ctx.toState(), ctx.transitionLabel(), ctx.username());
    }
}
