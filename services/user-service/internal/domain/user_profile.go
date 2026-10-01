package domain

import (
	"time"

	"github.com/google/uuid"
)

type ProfileStatus string

const (
	ProfileStatusActive    ProfileStatus = "ACTIVE"
	ProfileStatusSuspended ProfileStatus = "SUSPENDED"
	ProfileStatusPending   ProfileStatus = "PENDING"
)

type UserProfile struct {
	ID            uuid.UUID     `json:"id"`
	Username      string        `json:"username"`
	Email         string        `json:"email"`
	AvatarURL     *string       `json:"avatarUrl"`
	Bio           *string       `json:"bio"`
	PhoneNumber   *string       `json:"phoneNumber"`
	DateOfBirth   *string       `json:"dateOfBirth"`
	Locale        string        `json:"locale"`
	Timezone      string        `json:"timezone"`
	ProfileStatus ProfileStatus `json:"profileStatus"`
	CreatedAt     time.Time     `json:"createdAt"`
	UpdatedAt     time.Time     `json:"updatedAt"`
}

func NewUserProfile(id uuid.UUID, username, email string) *UserProfile {
	now := time.Now().UTC()
	return &UserProfile{
		ID:            id,
		Username:      username,
		Email:         email,
		Locale:        "ru",
		Timezone:      "Asia/Tashkent",
		ProfileStatus: ProfileStatusActive,
		CreatedAt:     now,
		UpdatedAt:     now,
	}
}
