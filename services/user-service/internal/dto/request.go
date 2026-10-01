package dto

import (
	"errors"
	"time"
)

type UpdateProfileRequest struct {
	AvatarURL   *string `json:"avatarUrl"`
	Bio         *string `json:"bio"`
	PhoneNumber *string `json:"phoneNumber"`
	DateOfBirth *string `json:"dateOfBirth"`
	Locale      *string `json:"locale"`
	Timezone    *string `json:"timezone"`
}

func (r *UpdateProfileRequest) Validate() error {
	if r.AvatarURL != nil && len(*r.AvatarURL) > 500 {
		return errors.New("avatarUrl must not exceed 500 characters")
	}
	if r.Bio != nil && len(*r.Bio) > 2000 {
		return errors.New("bio must not exceed 2000 characters")
	}
	if r.PhoneNumber != nil && len(*r.PhoneNumber) > 20 {
		return errors.New("phoneNumber must not exceed 20 characters")
	}
	if r.Locale != nil && len(*r.Locale) > 10 {
		return errors.New("locale must not exceed 10 characters")
	}
	if r.Timezone != nil && len(*r.Timezone) > 50 {
		return errors.New("timezone must not exceed 50 characters")
	}
	if r.DateOfBirth != nil && *r.DateOfBirth != "" {
		if _, err := time.Parse("2006-01-02", *r.DateOfBirth); err != nil {
			return errors.New("dateOfBirth must be in format YYYY-MM-DD")
		}
	}
	return nil
}
