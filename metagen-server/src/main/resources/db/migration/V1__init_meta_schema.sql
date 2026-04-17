-- =============================================================
-- Metagen Platform — V1: Meta schema initialisation
--
-- This migration creates all tables that describe the metadata
-- model (entities, fields, workflows, roles, permissions, views).
-- Application data tables (data_*) are created dynamically by
-- the engine when an entity is deployed; they are NOT managed
-- by Flyway.
-- =============================================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================================
-- WORKFLOWS  (must exist before meta_entity FK)
-- =============================================================

CREATE TABLE meta_workflow (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(100) NOT NULL,
    label       VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_meta_workflow_name UNIQUE (name)
);

COMMENT ON TABLE  meta_workflow           IS 'Lifecycle / state-machine definition reusable across entities.';
COMMENT ON COLUMN meta_workflow.name      IS 'Technical identifier in snake_case, used in code references.';


CREATE TABLE meta_state (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_id UUID         NOT NULL REFERENCES meta_workflow(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    label       VARCHAR(200) NOT NULL,
    is_initial  BOOLEAN      NOT NULL DEFAULT FALSE,
    is_final    BOOLEAN      NOT NULL DEFAULT FALSE,
    color       VARCHAR(20),
    position    INT          NOT NULL DEFAULT 0,

    CONSTRAINT uq_meta_state_workflow_name UNIQUE (workflow_id, name)
);

COMMENT ON TABLE  meta_state            IS 'A single lifecycle stage within a workflow.';
COMMENT ON COLUMN meta_state.is_initial IS 'Exactly one state per workflow must be initial.';
COMMENT ON COLUMN meta_state.is_final   IS 'Terminal states cannot have outgoing transitions.';


CREATE TABLE meta_transition (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_id      UUID         NOT NULL REFERENCES meta_workflow(id)  ON DELETE CASCADE,
    from_state_id    UUID         NOT NULL REFERENCES meta_state(id),
    to_state_id      UUID         NOT NULL REFERENCES meta_state(id),
    label            VARCHAR(200) NOT NULL,
    condition_script TEXT,
    action_script    TEXT,
    required_role    VARCHAR(100),

    CONSTRAINT fk_transition_from_state FOREIGN KEY (from_state_id) REFERENCES meta_state(id),
    CONSTRAINT fk_transition_to_state   FOREIGN KEY (to_state_id)   REFERENCES meta_state(id)
);

COMMENT ON TABLE  meta_transition                  IS 'Directed edge between two states — triggered by a user action.';
COMMENT ON COLUMN meta_transition.condition_script IS 'Groovy script; must return true for the transition to proceed.';
COMMENT ON COLUMN meta_transition.action_script    IS 'Groovy script executed after the state change is persisted.';
COMMENT ON COLUMN meta_transition.required_role    IS 'If set, only users with this role can trigger the transition.';


-- =============================================================
-- ENTITIES
-- =============================================================

CREATE TABLE meta_entity (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(100) NOT NULL,
    label       VARCHAR(200) NOT NULL,
    table_name  VARCHAR(100) NOT NULL,
    description TEXT,
    soft_delete BOOLEAN      NOT NULL DEFAULT TRUE,
    auditable   BOOLEAN      NOT NULL DEFAULT TRUE,
    workflow_id UUID         REFERENCES meta_workflow(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_meta_entity_name       UNIQUE (name),
    CONSTRAINT uq_meta_entity_table_name UNIQUE (table_name),
    CONSTRAINT fk_entity_workflow        FOREIGN KEY (workflow_id) REFERENCES meta_workflow(id)
);

COMMENT ON TABLE  meta_entity            IS 'Describes a business object (table) managed by the platform.';
COMMENT ON COLUMN meta_entity.name       IS 'Technical snake_case identifier, used in URLs and code.';
COMMENT ON COLUMN meta_entity.table_name IS 'Actual PostgreSQL table; defaults to data_<name> by convention.';
COMMENT ON COLUMN meta_entity.soft_delete IS 'When true, DELETE sets deleted_at instead of removing the row.';
COMMENT ON COLUMN meta_entity.auditable  IS 'When true, created_at/updated_at/created_by/updated_by are auto-managed.';


-- =============================================================
-- FIELDS
-- =============================================================

CREATE TABLE meta_field (
    id                UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_id         UUID         NOT NULL REFERENCES meta_entity(id) ON DELETE CASCADE,
    name              VARCHAR(100) NOT NULL,
    label             VARCHAR(200) NOT NULL,
    type              VARCHAR(50)  NOT NULL,
    required          BOOLEAN      NOT NULL DEFAULT FALSE,
    unique_constraint BOOLEAN      NOT NULL DEFAULT FALSE,
    searchable        BOOLEAN      NOT NULL DEFAULT TRUE,
    default_value     TEXT,
    options           JSONB,
    position          INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_meta_field_entity_name UNIQUE (entity_id, name),
    CONSTRAINT fk_field_entity           FOREIGN KEY (entity_id) REFERENCES meta_entity(id) ON DELETE CASCADE
);

COMMENT ON TABLE  meta_field                   IS 'A column / attribute belonging to a meta_entity.';
COMMENT ON COLUMN meta_field.type              IS 'MetaFieldType enum value; drives SQL type + React component.';
COMMENT ON COLUMN meta_field.options           IS 'Type-specific config: enum values, decimal precision, reference target, etc.';
COMMENT ON COLUMN meta_field.unique_constraint IS 'When true, the engine adds a UNIQUE constraint on the data table column.';


-- =============================================================
-- RELATIONS
-- =============================================================

CREATE TABLE meta_relation (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_entity_id UUID         NOT NULL REFERENCES meta_entity(id) ON DELETE CASCADE,
    target_entity_id UUID         NOT NULL REFERENCES meta_entity(id) ON DELETE CASCADE,
    type             VARCHAR(20)  NOT NULL
                        CHECK (type IN ('ONE_TO_MANY','MANY_TO_ONE','MANY_TO_MANY','ONE_TO_ONE')),
    field_name       VARCHAR(100) NOT NULL,
    label            VARCHAR(200),
    cascade_delete   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_relation_source FOREIGN KEY (source_entity_id) REFERENCES meta_entity(id) ON DELETE CASCADE,
    CONSTRAINT fk_relation_target FOREIGN KEY (target_entity_id) REFERENCES meta_entity(id) ON DELETE CASCADE
);

COMMENT ON TABLE  meta_relation                IS 'Relationship between two entities (FK / join table).';
COMMENT ON COLUMN meta_relation.field_name     IS 'Name used in API responses and sub-resource URLs.';
COMMENT ON COLUMN meta_relation.cascade_delete IS 'When true, deleting the source cascades to related records.';


-- =============================================================
-- ROLES & PERMISSIONS
-- =============================================================

CREATE TABLE meta_role (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(100) NOT NULL,
    label       VARCHAR(200) NOT NULL,
    description TEXT,
    built_in    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_meta_role_name UNIQUE (name)
);

COMMENT ON TABLE  meta_role          IS 'RBAC role assigned to platform users.';
COMMENT ON COLUMN meta_role.built_in IS 'Platform-seeded roles (ADMIN, USER) that cannot be deleted.';


CREATE TABLE meta_permission (
    id               UUID    PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id          UUID    NOT NULL REFERENCES meta_role(id)   ON DELETE CASCADE,
    entity_id        UUID    NOT NULL REFERENCES meta_entity(id) ON DELETE CASCADE,
    can_read         BOOLEAN NOT NULL DEFAULT FALSE,
    can_write        BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete       BOOLEAN NOT NULL DEFAULT FALSE,
    row_filter_script TEXT,
    field_mask_json  JSONB,

    CONSTRAINT uq_meta_permission_role_entity UNIQUE (role_id, entity_id),
    CONSTRAINT fk_permission_role             FOREIGN KEY (role_id)    REFERENCES meta_role(id)   ON DELETE CASCADE,
    CONSTRAINT fk_permission_entity           FOREIGN KEY (entity_id)  REFERENCES meta_entity(id) ON DELETE CASCADE
);

COMMENT ON TABLE  meta_permission                   IS 'Grants a role specific access rights on an entity.';
COMMENT ON COLUMN meta_permission.row_filter_script IS 'Groovy returning a SQL WHERE fragment for row-level isolation.';
COMMENT ON COLUMN meta_permission.field_mask_json   IS '{"hidden":["salary"],"readonly":["status"]} for field-level masking.';


-- =============================================================
-- VIEWS
-- =============================================================

CREATE TABLE meta_view (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    name       VARCHAR(100) NOT NULL,
    label      VARCHAR(200) NOT NULL,
    entity_id  UUID         REFERENCES meta_entity(id) ON DELETE CASCADE,
    type       VARCHAR(50)  NOT NULL
                   CHECK (type IN ('LIST','FORM','DASHBOARD','KANBAN','CALENDAR','CHART')),
    config     JSONB        NOT NULL DEFAULT '{}',
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_meta_view_name    UNIQUE (name),
    CONSTRAINT fk_view_entity       FOREIGN KEY (entity_id) REFERENCES meta_entity(id) ON DELETE CASCADE
);

COMMENT ON TABLE  meta_view            IS 'Named view configuration for an entity (list, form, kanban, etc.).';
COMMENT ON COLUMN meta_view.config     IS 'JSON config structure depends on view type; see MetaView Javadoc.';
COMMENT ON COLUMN meta_view.is_default IS 'When true, this view is shown by default for its entity + type.';


-- =============================================================
-- INDEXES  (performance for the engine's hot paths)
-- =============================================================

CREATE INDEX idx_meta_field_entity        ON meta_field(entity_id);
CREATE INDEX idx_meta_field_searchable    ON meta_field(entity_id) WHERE searchable = TRUE;
CREATE INDEX idx_meta_relation_source     ON meta_relation(source_entity_id);
CREATE INDEX idx_meta_relation_target     ON meta_relation(target_entity_id);
CREATE INDEX idx_meta_state_workflow      ON meta_state(workflow_id);
CREATE INDEX idx_meta_transition_workflow ON meta_transition(workflow_id);
CREATE INDEX idx_meta_transition_from     ON meta_transition(from_state_id);
CREATE INDEX idx_meta_permission_role     ON meta_permission(role_id);
CREATE INDEX idx_meta_permission_entity   ON meta_permission(entity_id);
CREATE INDEX idx_meta_view_entity         ON meta_view(entity_id);


-- =============================================================
-- SEED DATA  (built-in roles)
-- =============================================================

INSERT INTO meta_role (id, name, label, description, built_in) VALUES
    (uuid_generate_v4(), 'ADMIN', 'Administrateur',
     'Full access to all entities and the Studio configuration.', TRUE),
    (uuid_generate_v4(), 'USER',  'Utilisateur',
     'Default read access; override per entity via meta_permission.', TRUE);
