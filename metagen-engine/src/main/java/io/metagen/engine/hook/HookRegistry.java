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

    private final Map<String, List<PreSaveHook>>  preSaveHooks;
    private final Map<String, List<PostSaveHook>> postSaveHooks;

    public HookRegistry(List<PreSaveHook> preHooks, List<PostSaveHook> postHooks) {
        this.preSaveHooks = preHooks.stream()
            .sorted(Comparator.comparingInt(PreSaveHook::order))
            .collect(Collectors.groupingBy(PreSaveHook::entityName));
        this.postSaveHooks = postHooks.stream()
            .sorted(Comparator.comparingInt(PostSaveHook::order))
            .collect(Collectors.groupingBy(PostSaveHook::entityName));

        if (!preHooks.isEmpty() || !postHooks.isEmpty()) {
            log.info("Registered {} pre-save and {} post-save hook(s)", preHooks.size(), postHooks.size());
        }
    }

    /** Runs pre-save hooks in order; stops at the first one that aborts. */
    public void executePreSave(HookContext ctx) {
        for (PreSaveHook hook : preSaveHooks.getOrDefault(ctx.getEntity().getName(), List.of())) {
            if (ctx.isAborted()) break;
            log.debug("Pre-save hook '{}' on '{}'", hook.getClass().getSimpleName(), ctx.getEntity().getName());
            hook.execute(ctx);
        }
    }

    /** Runs post-save hooks in order, inside the write transaction. */
    public void executePostSave(HookContext ctx) {
        for (PostSaveHook hook : postSaveHooks.getOrDefault(ctx.getEntity().getName(), List.of())) {
            log.debug("Post-save hook '{}' on '{}'", hook.getClass().getSimpleName(), ctx.getEntity().getName());
            hook.execute(ctx);
        }
    }
}
