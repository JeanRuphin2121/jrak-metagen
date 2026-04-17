package io.metagen.core.domain;

/**
 * Cardinality of a relation between two {@link MetaEntity} instances.
 *
 * <p>These types mirror standard JPA / relational concepts but are expressed
 * at the metadata level so the runtime engine can generate the correct
 * JOIN queries and UI components (inline list vs. lookup field).</p>
 */
public enum MetaRelationType {

    /**
     * One source record → many target records.
     * Example: one Customer → many Orders.
     * UI: inline sub-list on the source form.
     */
    ONE_TO_MANY,

    /**
     * Many source records → one target record.
     * Example: many Orders → one Customer.
     * UI: lookup / select field on the source form.
     */
    MANY_TO_ONE,

    /**
     * Many source records ↔ many target records.
     * Backed by a join table named {@code <source>_<target>}.
     * Example: many Products ↔ many Categories.
     * UI: multi-select field.
     */
    MANY_TO_MANY,

    /**
     * One source record ↔ one target record.
     * Example: one User ↔ one UserProfile.
     * UI: inline embedded form.
     */
    ONE_TO_ONE
}
