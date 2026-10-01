package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"dev.mathalama/userservice/internal/domain"
	"github.com/google/uuid"
	_ "github.com/lib/pq"
)

var (
	ErrNotFound = errors.New("user profile not found")
)

type UserProfileRepository interface {
	ExistsByID(ctx context.Context, id uuid.UUID) (bool, error)
	FindByID(ctx context.Context, id uuid.UUID) (*domain.UserProfile, error)
	Save(ctx context.Context, profile *domain.UserProfile) (*domain.UserProfile, error)
}

type PostgresUserProfileRepository struct {
	db *sql.DB
}

func NewPostgresUserProfileRepository(db *sql.DB) *PostgresUserProfileRepository {
	return &PostgresUserProfileRepository{db: db}
}

func (r *PostgresUserProfileRepository) ExistsByID(ctx context.Context, id uuid.UUID) (bool, error) {
	var exists bool
	query := `SELECT EXISTS(SELECT 1 FROM user_profiles WHERE id = $1)`
	err := r.db.QueryRowContext(ctx, query, id).Scan(&exists)
	if err != nil {
		return false, fmt.Errorf("exists check failed: %w", err)
	}
	return exists, nil
}

func (r *PostgresUserProfileRepository) FindByID(ctx context.Context, id uuid.UUID) (*domain.UserProfile, error) {
	query := `
		SELECT id, username, email, avatar_url, bio, phone_number,
		       TO_CHAR(date_of_birth, 'YYYY-MM-DD') AS dob,
		       locale, timezone, profile_status, created_at, updated_at
		FROM user_profiles
		WHERE id = $1
	`
	row := r.db.QueryRowContext(ctx, query, id)

	var p domain.UserProfile
	var status string
	var dob sql.NullString
	var avatar, bio, phone sql.NullString

	err := row.Scan(
		&p.ID,
		&p.Username,
		&p.Email,
		&avatar,
		&bio,
		&phone,
		&dob,
		&p.Locale,
		&p.Timezone,
		&status,
		&p.CreatedAt,
		&p.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, fmt.Errorf("scan profile failed: %w", err)
	}

	p.ProfileStatus = domain.ProfileStatus(status)
	if avatar.Valid {
		p.AvatarURL = &avatar.String
	}
	if bio.Valid {
		p.Bio = &bio.String
	}
	if phone.Valid {
		p.PhoneNumber = &phone.String
	}
	if dob.Valid {
		p.DateOfBirth = &dob.String
	}

	return &p, nil
}

func (r *PostgresUserProfileRepository) Save(ctx context.Context, p *domain.UserProfile) (*domain.UserProfile, error) {
	now := time.Now().UTC()
	p.UpdatedAt = now
	if p.CreatedAt.IsZero() {
		p.CreatedAt = now
	}

	var dobVal interface{}
	if p.DateOfBirth != nil && *p.DateOfBirth != "" {
		dobVal = *p.DateOfBirth
	}

	query := `
		INSERT INTO user_profiles (
			id, username, email, avatar_url, bio, phone_number,
			date_of_birth, locale, timezone, profile_status, created_at, updated_at
		) VALUES (
			$1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12
		)
		ON CONFLICT (id) DO UPDATE SET
			username = EXCLUDED.username,
			email = EXCLUDED.email,
			avatar_url = EXCLUDED.avatar_url,
			bio = EXCLUDED.bio,
			phone_number = EXCLUDED.phone_number,
			date_of_birth = EXCLUDED.date_of_birth,
			locale = EXCLUDED.locale,
			timezone = EXCLUDED.timezone,
			profile_status = EXCLUDED.profile_status,
			updated_at = EXCLUDED.updated_at
		RETURNING id, username, email, avatar_url, bio, phone_number,
		          TO_CHAR(date_of_birth, 'YYYY-MM-DD') AS dob,
		          locale, timezone, profile_status, created_at, updated_at
	`

	row := r.db.QueryRowContext(ctx, query,
		p.ID,
		p.Username,
		p.Email,
		p.AvatarURL,
		p.Bio,
		p.PhoneNumber,
		dobVal,
		p.Locale,
		p.Timezone,
		string(p.ProfileStatus),
		p.CreatedAt,
		p.UpdatedAt,
	)

	var saved domain.UserProfile
	var status string
	var dob sql.NullString
	var avatar, bio, phone sql.NullString

	err := row.Scan(
		&saved.ID,
		&saved.Username,
		&saved.Email,
		&avatar,
		&bio,
		&phone,
		&dob,
		&saved.Locale,
		&saved.Timezone,
		&status,
		&saved.CreatedAt,
		&saved.UpdatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("failed saving user profile: %w", err)
	}

	saved.ProfileStatus = domain.ProfileStatus(status)
	if avatar.Valid {
		saved.AvatarURL = &avatar.String
	}
	if bio.Valid {
		saved.Bio = &bio.String
	}
	if phone.Valid {
		saved.PhoneNumber = &phone.String
	}
	if dob.Valid {
		saved.DateOfBirth = &dob.String
	}

	return &saved, nil
}
