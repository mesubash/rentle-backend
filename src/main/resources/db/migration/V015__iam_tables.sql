CREATE TABLE permissions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key           VARCHAR(120) NOT NULL,
    domain        VARCHAR(40)  NOT NULL,
    resource      VARCHAR(40)  NOT NULL,
    action        VARCHAR(40)  NOT NULL,
    description   VARCHAR(300),
    is_deprecated BOOLEAN      NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_permissions_key UNIQUE (key),
    CONSTRAINT chk_permission_key_format
        CHECK (key = domain || '.' || resource || '.' || action
               AND key ~ '^[a-z_]+\.[a-z_]+\.[a-z_]+$')
);

CREATE TABLE scopes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id  UUID REFERENCES scopes(id),
    type       VARCHAR(20)  NOT NULL,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(60)  NOT NULL,
    display_name   VARCHAR(120) NOT NULL,
    description    VARCHAR(300),
    is_system_role BOOLEAN      NOT NULL DEFAULT false,
    owner_scope_id UUID REFERENCES scopes(id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TRIGGER trg_roles_updated_at
BEFORE UPDATE ON roles FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE assignments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id UUID NOT NULL REFERENCES users(id),
    role_id    UUID NOT NULL REFERENCES roles(id),
    scope_id   UUID NOT NULL REFERENCES scopes(id),
    granted_by UUID REFERENCES users(id),
    revoked_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_assignments_live
    ON assignments(subject_id, role_id, scope_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_assignments_subject ON assignments(subject_id) WHERE revoked_at IS NULL;
