-- =============================================================
-- Metagen Platform — V2: Platform users table
--
-- Stores authentication credentials for platform users.
-- User-to-role assignments link to meta_role.
-- =============================================================

CREATE TABLE platform_user (
    id           UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    username     VARCHAR(100) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    password     VARCHAR(255) NOT NULL,   -- BCrypt hash
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_user_username UNIQUE (username),
    CONSTRAINT uq_user_email    UNIQUE (email)
);

COMMENT ON TABLE  platform_user          IS 'Platform authentication credentials.';
COMMENT ON COLUMN platform_user.password IS 'BCrypt-hashed password — never stored in plain text.';


CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES meta_role(id)     ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

COMMENT ON TABLE user_role IS 'Many-to-many: which roles a platform user holds.';


CREATE INDEX idx_user_role_user ON user_role(user_id);
CREATE INDEX idx_user_role_role ON user_role(role_id);
