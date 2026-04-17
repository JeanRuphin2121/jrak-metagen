package io.metagen.engine.hook;

import io.metagen.core.domain.MetaEntity;
import lombok.Getter;

import java.util.*;

/**
 * Context object passed to every {@link PreSaveHook} invocation.
 *
 * <p>Hooks can read the record data, modify it, add validation errors,
 * or abort the operation entirely by calling {@link #abort(String)}.</p>
 *
 * <h2>Immutability</h2>
 * The {@code record} map is a mutable copy — hooks may safely modify it.
 * The original request body is not affected.
 *
 * <h2>Example hook</h2>
 * <pre>
 * {@literal @}Component
 * {@literal @}BusinessHook(entity = "supplier", event = ON_SAVE)
 * public class SupplierSiretValidator implements PreSaveHook {
 *
 *     {@literal @}Override
 *     public void execute(HookContext ctx) {
 *         String siret = (String) ctx.getRecord().get("siret");
 *         if (siret != null && siret.length() != 14) {
 *             ctx.addError("siret", "SIRET must be 14 digits");
 *         }
 *     }
 * }
 * </pre>
 */
@Getter
public class HookContext {

    public enum Operation { CREATE, UPDATE, DELETE }

    private final MetaEntity              entity;
    private final Operation               operation;
    private final UUID                    recordId;     // null on CREATE before insert
    private final Map<String, Object>     record;       // mutable copy of the record
    private final String                  username;
    private final Map<String, String>     errors   = new LinkedHashMap<>();
    private       boolean                 aborted  = false;
    private       String                  abortReason;

    private HookContext(MetaEntity entity, Operation op, UUID id,
                        Map<String, Object> record, String username) {
        this.entity    = entity;
        this.operation = op;
        this.recordId  = id;
        this.record    = new LinkedHashMap<>(record);
        this.username  = username;
    }

    // ── Factories ─────────────────────────────────────────────────────

    public static HookContext forCreate(MetaEntity entity, Map<String, Object> body, String user) {
        return new HookContext(entity, Operation.CREATE, null, body, user);
    }

    public static HookContext forUpdate(MetaEntity entity, UUID id, Map<String, Object> body, String user) {
        return new HookContext(entity, Operation.UPDATE, id, body, user);
    }

    public static HookContext forDelete(MetaEntity entity, UUID id, String user) {
        return new HookContext(entity, Operation.DELETE, id, Map.of(), user);
    }

    // ── Hook API ──────────────────────────────────────────────────────

    /**
     * Adds a field-level validation error.
     * The operation will be rejected if any errors are present after all hooks run.
     *
     * @param field   field name (e.g. {@code "siret"})
     * @param message human-readable error message
     */
    public void addError(String field, String message) {
        errors.put(field, message);
    }

    /**
     * Aborts the entire operation with a reason message.
     * No further hooks are executed after abort is called.
     */
    public void abort(String reason) {
        this.aborted     = true;
        this.abortReason = reason;
    }

    /** Returns true if any validation error was added. */
    public boolean hasErrors() {
        return !errors.isEmpty() || aborted;
    }
}
