package io.metagen.engine.hook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registry of all {@link PreSaveHook} beans declared in the application context.
 *
 * <p>Spring auto-discovers every {@code @Component} that implements
 * {@code PreSaveHook} and injects them into this registry. No manual
 * registration is needed.</p>
 *
 * <h2>Hook resolution</h2>
 * <ol>
 *   <li>On startup, all {@code PreSaveHook} beans are grouped by entity name.</li>
 *   <li>On each save, hooks for the target entity are retrieved and executed
 *       in {@link PreSaveHook#order()} ascending order.</li>
 *   <li>Execution stops early if a hook calls {@link HookContext#abort(String)}.</li>
 * </ol>
 */
@Slf4j
@Component
public class HookRegistry {

    /** Map of entity name → hooks sorted by order(). */
    private final Map<String, List<PreSaveHook>> preSaveHooks;

    public HookRegistry(List<PreSaveHook> allHooks) {
        this.preSaveHooks = allHooks.stream()
            .sorted(Comparator.comparingInt(PreSaveHook::order))
            .collect(Collectors.groupingBy(PreSaveHook::entityName));

        if (!allHooks.isEmpty()) {
            log.info("Registered {} pre-save hook(s): {}",
                allHooks.size(),
                allHooks.stream()
                    .map(h -> h.entityName() + "/" + h.getClass().getSimpleName())
                    .toList());
        }
    }

    /**
     * Executes all pre-save hooks registered for the entity in {@code ctx}.
     * Stops early if a hook aborts the operation.
     *
     * @param ctx mutable hook context (errors and record modifications accumulate here)
     */
    public void executePreSave(HookContext ctx) {
        String entity = ctx.getEntity().getName();
        List<PreSaveHook> hooks = preSaveHooks.getOrDefault(entity, List.of());

        for (PreSaveHook hook : hooks) {
            if (ctx.isAborted()) {
                log.debug("Hook chain aborted before '{}' for entity '{}'",
                          hook.getClass().getSimpleName(), entity);
                break;
            }
            log.debug("Executing pre-save hook '{}' for entity '{}'",
                      hook.getClass().getSimpleName(), entity);
            hook.execute(ctx);
        }
    }

    /**
     * Post-save notification — currently logs only.
     * Will be extended in Phase 3 to support async event hooks.
     */
    public void executePostSave(HookContext ctx) {
        log.debug("Post-save for entity '{}' op={}", ctx.getEntity().getName(), ctx.getOperation());
    }
}
