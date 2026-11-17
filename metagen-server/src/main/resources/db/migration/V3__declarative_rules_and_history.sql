-- ============================================================================
-- V3 — Declarative rules + workflow history
--
-- Business rules are no longer free-form scripts stored in text columns:
--   * row filters and transition conditions are declarative JSON conditions,
--     compiled to parameterised SQL or evaluated in memory by the engine;
--   * transition actions reference a named WorkflowAction Java bean.
-- The columns are renamed accordingly. Any legacy script content is cleared:
-- it was Groovy / raw SQL and cannot be interpreted by the new engine.
-- ============================================================================

ALTER TABLE meta_permission RENAME COLUMN row_filter_script TO row_filter;
UPDATE meta_permission SET row_filter = NULL
 WHERE row_filter IS NOT NULL AND btrim(row_filter) NOT LIKE '{%';
COMMENT ON COLUMN meta_permission.row_filter IS
    'Declarative JSON condition restricting visible rows, e.g. {"field":"created_by","op":"eq","value":"$user"}.';

ALTER TABLE meta_transition RENAME COLUMN condition_script TO condition_expr;
ALTER TABLE meta_transition RENAME COLUMN action_script    TO action_name;
UPDATE meta_transition SET condition_expr = NULL
 WHERE condition_expr IS NOT NULL AND btrim(condition_expr) NOT LIKE '{%';
UPDATE meta_transition SET action_name = NULL
 WHERE action_name IS NOT NULL AND action_name !~ '^[a-z][a-z0-9_]*$';
ALTER TABLE meta_transition ALTER COLUMN action_name TYPE VARCHAR(100);
COMMENT ON COLUMN meta_transition.condition_expr IS
    'Declarative JSON condition evaluated on the record before the transition.';
COMMENT ON COLUMN meta_transition.action_name IS
    'Name of a WorkflowAction Spring bean run in the transition''s transaction.';

-- ── Workflow history ────────────────────────────────────────────────────────
CREATE TABLE workflow_history (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_name      VARCHAR(100) NOT NULL,
    record_id        UUID         NOT NULL,
    from_state       VARCHAR(100),
    to_state         VARCHAR(100) NOT NULL,
    transition_label VARCHAR(200) NOT NULL,
    username         VARCHAR(255) NOT NULL,
    comment          TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE workflow_history IS 'Audit trail of every workflow transition executed on a record.';

CREATE INDEX idx_workflow_history_record ON workflow_history(entity_name, record_id, created_at);
