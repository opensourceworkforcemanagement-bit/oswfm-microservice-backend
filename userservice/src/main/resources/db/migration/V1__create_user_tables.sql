-- Considering user_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
CREATE TABLE oswfm_users (
	user_id SERIAL PRIMARY KEY,
	user_status int2 NOT NULL, 
	first_name varchar(255) NULL,
    middle_name varchar(255) NULL,
	last_name varchar(255) NULL,
	user_name varchar(255) NOT NULL
);
-- Index for faster queries
CREATE INDEX idx_user_userstatus ON oswfm_users(user_status);
CREATE INDEX idx_user_user_id ON oswfm_users(user_id);
CREATE INDEX idx_user_user_name ON oswfm_users(user_name);

CREATE TABLE oswfm_users_audit_log (
	oswfm_users_audit_log_id SERIAL PRIMARY KEY,
	user_id INTEGER NOT NULL REFERENCES oswfm_users(user_id),
	created_at timestamp(6) NULL, -- Considering TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
	created_by INTEGER,
	operation_type_id INTEGER NULL
);


-- Index for faster queries
CREATE INDEX idx_oswfm_users_audit_log_user_id ON oswfm_users_audit_log(user_id);
CREATE INDEX idx_oswfm_users_audit_log_created_at ON oswfm_users_audit_log(created_at);

-- User group types
CREATE TABLE user_group_type (
    user_group_type_id SERIAL PRIMARY KEY,
    type_name VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for faster queries
CREATE INDEX idx_user_group_type_type_name ON user_group_type(type_name);

-- User groups
CREATE TABLE user_groups (
    group_id SERIAL PRIMARY KEY,
    group_name VARCHAR(255) UNIQUE NOT NULL,
	user_group_type_id INTEGER NOT NULL REFERENCES user_group_type(user_group_type_id),
    description TEXT,
    parent_group_id INTEGER REFERENCES user_groups(group_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for faster queries
CREATE INDEX idx_user_groups_group_name ON user_groups(group_name);
CREATE INDEX idx_user_groups_parent_group_id ON user_groups(parent_group_id);
CREATE INDEX idx_user_groups_user_group_type_id ON user_groups(user_group_type_id);

CREATE TABLE user_groups_audit_log (
	audit_log_id SERIAL PRIMARY KEY,
	group_id INTEGER NOT NULL REFERENCES user_groups(group_id),
	changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
	changed_by INTEGER,
	change_type VARCHAR(50) NOT NULL
);

-- Index for faster queries
CREATE INDEX idx_user_groups_audit_log_group_id ON user_groups_audit_log(group_id);
CREATE INDEX idx_user_groups_audit_log_changed_at ON user_groups_audit_log(changed_at);

-- User group memberships
CREATE TABLE user_group_memberships (
    membership_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES oswfm_users(user_id) ON DELETE CASCADE,
    group_id INTEGER NOT NULL REFERENCES user_groups(group_id) ON DELETE CASCADE,
    UNIQUE(user_id, group_id)
);

-- Index for faster queries
CREATE INDEX idx_user_group_memberships_user_id ON user_group_memberships(user_id);
CREATE INDEX idx_user_group_memberships_group_id ON user_group_memberships(group_id);

CREATE TABLE user_group_memberships_audit_log (
	audit_log_id SERIAL PRIMARY KEY,
	membership_id INTEGER NOT NULL REFERENCES user_group_memberships(membership_id),
	changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
	changed_by INTEGER,
	change_type VARCHAR(50) NOT NULL
);

-- Index for faster queries
CREATE INDEX idx_user_group_memberships_audit_log_membership_id ON user_group_memberships_audit_log(membership_id);
CREATE INDEX idx_user_group_memberships_audit_log_changed_at ON user_group_memberships_audit_log(changed_at);

CREATE TABLE IF NOT EXISTS users_profile_settings (
	profile_setting_id  SERIAL PRIMARY KEY,
	user_id INTEGER NOT NULL REFERENCES oswfm_users(user_id),
	setting_key VARCHAR(100) NOT NULL,
	setting_value VARCHAR(255) NOT NULL
);

-- Index for faster queries
CREATE INDEX idx_users_profile_settings_user_id ON users_profile_settings(user_id);
CREATE INDEX idx_users_profile_settings_setting_key ON users_profile_settings(setting_key);


CREATE TABLE IF NOT EXISTS users_login_history (
	login_history_id  SERIAL PRIMARY KEY,
	user_id INTEGER NOT NULL REFERENCES oswfm_users(user_id),
	login_timestamp timestamp(6) NULL,
	logout_timestamp timestamp(6) NULL,
	ip_address VARCHAR(45)
);
-- Index for faster queries
CREATE INDEX idx_users_login_history_user_id ON users_login_history(user_id);
CREATE INDEX idx_users_login_history_login_timestamp ON users_login_history(login_timestamp);
CREATE INDEX idx_users_login_history_ip_address ON users_login_history(ip_address);

CREATE TABLE IF NOT EXISTS users_password_reset_tokens (
	password_reset_token_id  SERIAL PRIMARY KEY,
	user_id INTEGER NOT NULL REFERENCES oswfm_users(user_id),
	token VARCHAR(255) NOT NULL,
	expiration TIMESTAMP(6) NOT NULL,
	used BOOLEAN DEFAULT FALSE
);
-- Index for faster queries
CREATE INDEX idx_users_password_reset_tokens_user_id ON users_password_reset_tokens(user_id);
CREATE INDEX idx_users_password_reset_tokens_token ON users_password_reset_tokens(token);
CREATE INDEX idx_users_password_reset_tokens_expiration ON users_password_reset_tokens(expiration);
CREATE INDEX idx_users_password_reset_tokens_used ON users_password_reset_tokens(used);

CREATE TABLE IF NOT EXISTS users_two_factor_auth (
	two_factor_auth_id  SERIAL PRIMARY KEY,
	user_id INTEGER NOT NULL REFERENCES oswfm_users(user_id),
	secret_key VARCHAR(255) NOT NULL,
	enabled BOOLEAN DEFAULT FALSE
);
-- Index for faster queries
CREATE INDEX idx_users_two_factor_auth_user_id ON users_two_factor_auth(user_id);
CREATE INDEX idx_users_two_factor_auth_enabled ON users_two_factor_auth(enabled);
CREATE INDEX idx_users_two_factor_auth_secret_key ON users_two_factor_auth(secret_key);

CREATE TABLE IF NOT EXISTS users_sessions (
	session_id  SERIAL PRIMARY KEY,
	user_id INTEGER NOT NULL REFERENCES oswfm_users(user_id),
	session_token VARCHAR(255) NOT NULL,
	created_at TIMESTAMP(6) NOT NULL,
	expires_at TIMESTAMP(6) NOT NULL
);
-- Index for faster queries
CREATE INDEX idx_users_sessions_user_id ON users_sessions(user_id);
CREATE INDEX idx_users_sessions_session_token ON users_sessions(session_token);
CREATE INDEX idx_users_sessions_expires_at ON users_sessions(expires_at);
CREATE INDEX idx_users_sessions_created_at ON users_sessions(created_at);

CREATE TABLE IF NOT EXISTS users_roles_permissions (
	role_permission_id  SERIAL PRIMARY KEY,
	role VARCHAR(50) NOT NULL,
	permission VARCHAR(50) NOT NULL
);
-- Index for faster queries
CREATE INDEX idx_users_roles_permissions_role ON users_roles_permissions(role);
CREATE INDEX idx_users_roles_permissions_permission ON users_roles_permissions(permission);
CREATE INDEX idx_users_roles_permissions_role_permission ON users_roles_permissions(role, permission);

-- Table for storing invalidated tokens to prevent their reuse
CREATE TABLE IF NOT EXISTS INVALID_TOKEN (
    ID VARCHAR(36) PRIMARY KEY,
    TOKEN_ID VARCHAR(255) NOT NULL,
    CREATED_AT TIMESTAMP(6) NULL,
    CREATED_BY VARCHAR(255) NULL,
    UPDATED_AT TIMESTAMP(6) NULL,
    UPDATED_BY VARCHAR(255) NULL
);

-- Index for faster token lookup
CREATE INDEX idx_invalid_token_token_id ON INVALID_TOKEN(TOKEN_ID);
CREATE INDEX idx_invalid_token_created_at ON INVALID_TOKEN(CREATED_AT);
