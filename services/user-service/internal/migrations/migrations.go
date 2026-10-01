package migrations

import (
	"context"
	"database/sql"
	"fmt"
	"log"
)

const schema = `
CREATE TABLE IF NOT EXISTS user_profiles (
    id              UUID PRIMARY KEY,
    username        VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(500),
    bio             TEXT,
    phone_number    VARCHAR(20),
    date_of_birth   DATE,
    locale          VARCHAR(10) DEFAULT 'ru',
    timezone        VARCHAR(50) DEFAULT 'Asia/Tashkent',
    profile_status  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_username ON user_profiles(username);
CREATE INDEX IF NOT EXISTS idx_user_profiles_email ON user_profiles(email);
CREATE INDEX IF NOT EXISTS idx_user_profiles_status ON user_profiles(profile_status);
`

func Run(ctx context.Context, db *sql.DB) error {
	log.Println("[Migrations] Running database migrations...")
	if _, err := db.ExecContext(ctx, schema); err != nil {
		return fmt.Errorf("failed executing schema migration: %w", err)
	}
	log.Println("[Migrations] Database migrations completed successfully.")
	return nil
}
