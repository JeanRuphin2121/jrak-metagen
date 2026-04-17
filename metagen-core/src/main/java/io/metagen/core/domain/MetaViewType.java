package io.metagen.core.domain;

/**
 * Types of auto-generated or custom views available for a {@link MetaEntity}.
 *
 * <p>Each view type maps to a specific React component in the frontend.
 * The rendering configuration (columns, filters, grouping, etc.) is stored
 * as JSON in {@link MetaView#config}.</p>
 */
public enum MetaViewType {

    /** Paginated table with sortable columns and filters. */
    LIST,

    /** Detail / edit form for a single record. */
    FORM,

    /** Dashboard with charts, KPIs, and widgets. */
    DASHBOARD,

    /**
     * Kanban board grouped by a workflow state field.
     * Requires the entity to have an associated {@link MetaWorkflow}.
     */
    KANBAN,

    /** Calendar view grouped by a DATE or DATETIME field. */
    CALENDAR,

    /** Chart view (bar, pie, line) aggregating entity data. */
    CHART
}
