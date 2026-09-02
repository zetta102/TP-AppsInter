CREATE TABLE refresh_token
(
    uuid       UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    token_hash VARCHAR(64)              NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (uuid),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (uuid) ON DELETE CASCADE,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
CREATE INDEX idx_refresh_token_expires_at ON refresh_token (expires_at);
