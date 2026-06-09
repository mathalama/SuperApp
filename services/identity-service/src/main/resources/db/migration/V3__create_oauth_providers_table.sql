CREATE TABLE oauth_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    provider_name VARCHAR(50) NOT NULL,
    provider_id VARCHAR(500) NOT NULL,
    provider_email VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    last_login_at TIMESTAMP,
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT unique_provider_per_user UNIQUE (user_id, provider_name)
);

CREATE INDEX idx_oauth_provider_name ON oauth_providers(provider_name);
CREATE INDEX idx_oauth_provider_id ON oauth_providers(provider_id);
CREATE INDEX idx_oauth_user_id ON oauth_providers(user_id);
CREATE INDEX idx_oauth_created_at ON oauth_providers(created_at);

COMMENT ON TABLE oauth_providers IS 'Tracks OAuth2 provider connections for users - allows one user to have multiple providers linked';
COMMENT ON COLUMN oauth_providers.provider_name IS 'OAuth provider: GOOGLE, GITHUB, etc';
COMMENT ON COLUMN oauth_providers.provider_id IS 'The unique ID from the OAuth provider (e.g., Google sub claim)';
COMMENT ON COLUMN oauth_providers.provider_email IS 'Email from OAuth provider (may differ from user email)';
COMMENT ON COLUMN oauth_providers.last_login_at IS 'Last time user logged in via this provider';
