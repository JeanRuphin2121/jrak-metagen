package io.metagen.engine.hook;

/**
 * Business hook invoked after a record has been inserted or updated, inside
 * the same database transaction. Throwing rolls the write back.
 *
 * <p>Implement as a Spring bean; like {@link PreSaveHook} it is plain Java and
 * therefore unit-testable with JUnit.
 */
public interface PostSaveHook {

    /** Technical name of the entity this hook applies to. */
    String entityName();

    /** {@code ctx.getRecord()} holds the record as stored, including system columns. */
    void execute(HookContext ctx);

    default int order() {
        return 100;
    }
}
