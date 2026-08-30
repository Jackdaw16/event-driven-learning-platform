CREATE TABLE auth_users (
    id UUID PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL,
    principal_id UUID,
    CONSTRAINT auth_users_username_not_blank CHECK (btrim(username) <> ''),
    CONSTRAINT auth_users_password_hash_not_blank CHECK (btrim(password_hash) <> ''),
    CONSTRAINT auth_users_role_valid CHECK (role IN ('ADMIN', 'INSTRUCTOR', 'STUDENT')),
    CONSTRAINT auth_users_principal_id_required_for_non_admin CHECK (
        role = 'ADMIN' OR principal_id IS NOT NULL
    )
);
