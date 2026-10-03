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
    kyc_status      VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS kyc_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED';

CREATE INDEX IF NOT EXISTS idx_user_profiles_username ON user_profiles(username);
CREATE INDEX IF NOT EXISTS idx_user_profiles_email ON user_profiles(email);
CREATE INDEX IF NOT EXISTS idx_user_profiles_status ON user_profiles(profile_status);
CREATE INDEX IF NOT EXISTS idx_user_profiles_kyc_status ON user_profiles(kyc_status);

CREATE TABLE IF NOT EXISTS wallets (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    currency        VARCHAR(10) NOT NULL,
    balance         NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wallets_user_currency UNIQUE(user_id, currency)
);

CREATE INDEX IF NOT EXISTS idx_wallets_user_id ON wallets(user_id);

CREATE TABLE IF NOT EXISTS transactions (
    id                      UUID PRIMARY KEY,
    idempotency_key         VARCHAR(100) UNIQUE,
    source_wallet_id        UUID REFERENCES wallets(id),
    destination_wallet_id   UUID REFERENCES wallets(id),
    source_user_id          UUID REFERENCES user_profiles(id),
    destination_user_id     UUID REFERENCES user_profiles(id),
    type                    VARCHAR(30) NOT NULL,
    amount                  NUMERIC(18, 4) NOT NULL,
    currency                VARCHAR(10) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    description             TEXT,
    failure_reason          TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transactions_source_user ON transactions(source_user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_destination_user ON transactions(destination_user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at DESC);
`

func Run(ctx context.Context, db *sql.DB) error {
	log.Println("[Migrations] Running database migrations...")
	if _, err := db.ExecContext(ctx, schema); err != nil {
		return fmt.Errorf("failed executing schema migration: %w", err)
	}
	log.Println("[Migrations] Database migrations completed successfully.")
	return nil
}
