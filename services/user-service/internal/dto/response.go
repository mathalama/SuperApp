package dto

import (
	"time"

	"dev.mathalama/userservice/internal/domain"
	"github.com/google/uuid"
)

type UserProfileResponse struct {
	ID            uuid.UUID `json:"id"`
	Username      string    `json:"username"`
	Email         string    `json:"email"`
	AvatarURL     *string   `json:"avatarUrl"`
	Bio           *string   `json:"bio"`
	PhoneNumber   *string   `json:"phoneNumber"`
	DateOfBirth   *string   `json:"dateOfBirth"`
	Locale        string    `json:"locale"`
	Timezone      string    `json:"timezone"`
	ProfileStatus string    `json:"profileStatus"`
	KycStatus     string    `json:"kycStatus"`
	CreatedAt     string    `json:"createdAt"`
	UpdatedAt     string    `json:"updatedAt"`
}

func FromDomain(u *domain.UserProfile) *UserProfileResponse {
	if u == nil {
		return nil
	}

	kycStat := string(u.KycStatus)
	if kycStat == "" {
		kycStat = string(domain.KycStatusNotStarted)
	}

	return &UserProfileResponse{
		ID:            u.ID,
		Username:      u.Username,
		Email:         u.Email,
		AvatarURL:     u.AvatarURL,
		Bio:           u.Bio,
		PhoneNumber:   u.PhoneNumber,
		DateOfBirth:   u.DateOfBirth,
		Locale:        u.Locale,
		Timezone:      u.Timezone,
		ProfileStatus: string(u.ProfileStatus),
		KycStatus:     kycStat,
		CreatedAt:     u.CreatedAt.Format(time.RFC3339),
		UpdatedAt:     u.UpdatedAt.Format(time.RFC3339),
	}
}

type ErrorResponse struct {
	Timestamp string            `json:"timestamp"`
	Status    int               `json:"status"`
	Error     string            `json:"error"`
	Message   string            `json:"message,omitempty"`
	Details   map[string]string `json:"details,omitempty"`
}
