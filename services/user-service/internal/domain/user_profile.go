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

type KycStatus string

const (
	KycStatusNotStarted   KycStatus = "NOT_STARTED"
	KycStatusPending      KycStatus = "PENDING"
	KycStatusVerified     KycStatus = "VERIFIED"
	KycStatusRejected     KycStatus = "REJECTED"
	KycStatusManualReview KycStatus = "MANUAL_REVIEW"
)

func (s KycStatus) IsValid() bool {
	switch s {
	case KycStatusNotStarted, KycStatusPending, KycStatusVerified, KycStatusRejected, KycStatusManualReview:
		return true
	default:
		return false
	}
}

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
	KycStatus     KycStatus     `json:"kycStatus"`
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
		KycStatus:     KycStatusNotStarted,
		CreatedAt:     now,
		UpdatedAt:     now,
	}
}
