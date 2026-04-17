package io.metagen.engine.hook;

/**
 * Interface for custom business hooks executed before a record is saved.
 *
 * <p>Implement this interface and annotate with {@code @Component} and
 * {@code @BusinessHook} to register a hook for a specific entity.
 * The hook can validate data, modify the record, or abort the operation.</p>
 *
 * <p>Unlike Simplicité's Groovy scripts stored in the database,
 * Metagen hooks are plain Java/Spring beans:</p>
 * <ul>
 *   <li>Fully testable with JUnit — no database needed</li>
 *   <li>IDE autocomplete, refactoring, and static analysis</li>
 *   <li>Can inject any Spring bean ({@code @Autowired})</li>
 *   <li>Versionned in Git alongside the rest of the codebase</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>
 * {@literal @}Component
 * public class InvoiceAmountValidator implements PreSaveHook {
 *
 *     {@literal @}Override
 *     public String entityName() { return "invoice"; }
 *
 *     {@literal @}Override
 *     public void execute(HookContext ctx) {
 *         var amount = (Number) ctx.getRecord().get("amount");
 *         if (amount == null || amount.doubleValue() <= 0) {
 *             ctx.addError("amount", "Amount must be positive");
 *         }
 *     }
 * }
 * </pre>
 */
public interface PreSaveHook {

    /**
     * The technical name of the entity this hook applies to.
     * Must match {@link io.metagen.core.domain.MetaEntity#getName()}.
     */
    String entityName();

    /**
     * Called before every CREATE or UPDATE on {@link #entityName()}.
     * Add errors to {@code ctx} to reject the operation.
     * Modify {@code ctx.getRecord()} to transform the data before saving.
     *
     * @param ctx mutable context containing the record data, user info, and error bag
     */
    void execute(HookContext ctx);

    /**
     * Priority (lower = executed first).
     * Override to control execution order when multiple hooks exist for one entity.
     */
    default int order() {
        return 100;
    }
}
