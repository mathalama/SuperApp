package service

import (
	"context"
	"fmt"
	"io"
	"log"

	"dev.mathalama/userservice/internal/domain"
	"dev.mathalama/userservice/internal/dto"
	"dev.mathalama/userservice/internal/repository"
	"dev.mathalama/userservice/internal/storage"
	"github.com/google/uuid"
)

type UserProfileService struct {
	repo          repository.UserProfileRepository
	avatarStorage storage.AvatarStorage
}

func NewUserProfileService(
	repo repository.UserProfileRepository,
	avatarStorage storage.AvatarStorage,
) *UserProfileService {
	return &UserProfileService{
		repo:          repo,
		avatarStorage: avatarStorage,
	}
}

func (s *UserProfileService) CreateProfile(ctx context.Context, userID uuid.UUID, username, email string) (*domain.UserProfile, error) {
	exists, err := s.repo.ExistsByID(ctx, userID)
	if err != nil {
		return nil, err
	}
	if exists {
		log.Printf("[UserProfileService] Profile already exists for userId=%s", userID)
		return s.repo.FindByID(ctx, userID)
	}

	profile := domain.NewUserProfile(userID, username, email)
	saved, err := s.repo.Save(ctx, profile)
	if err != nil {
		return nil, err
	}

	log.Printf("[UserProfileService] Created profile for userId=%s (username=%s)", userID, username)
	return saved, nil
}

func (s *UserProfileService) GetProfile(ctx context.Context, userID uuid.UUID) (*domain.UserProfile, error) {
	return s.repo.FindByID(ctx, userID)
}

func (s *UserProfileService) UpdateProfile(ctx context.Context, userID uuid.UUID, req *dto.UpdateProfileRequest) (*domain.UserProfile, error) {
	profile, err := s.repo.FindByID(ctx, userID)
	if err != nil {
		return nil, err
	}

	if req.AvatarURL != nil {
		profile.AvatarURL = req.AvatarURL
	}
	if req.Bio != nil {
		profile.Bio = req.Bio
	}
	if req.PhoneNumber != nil {
		profile.PhoneNumber = req.PhoneNumber
	}
	if req.DateOfBirth != nil {
		profile.DateOfBirth = req.DateOfBirth
	}
	if req.Locale != nil {
		profile.Locale = *req.Locale
	}
	if req.Timezone != nil {
		profile.Timezone = *req.Timezone
	}

	return s.repo.Save(ctx, profile)
}

func (s *UserProfileService) UploadAvatar(ctx context.Context, userID uuid.UUID, r io.Reader, size int64) (*domain.UserProfile, error) {
	profile, err := s.repo.FindByID(ctx, userID)
	if err != nil {
		return nil, err
	}

	if profile.AvatarURL != nil && *profile.AvatarURL != "" {
		_ = s.avatarStorage.DeleteAvatar(ctx, *profile.AvatarURL)
	}

	newAvatarURL, err := s.avatarStorage.UploadAvatar(ctx, userID, r, size)
	if err != nil {
		return nil, err
	}

	profile.AvatarURL = &newAvatarURL
	return s.repo.Save(ctx, profile)
}

func (s *UserProfileService) DeleteAvatar(ctx context.Context, userID uuid.UUID) (*domain.UserProfile, error) {
	profile, err := s.repo.FindByID(ctx, userID)
	if err != nil {
		return nil, err
	}

	if profile.AvatarURL != nil && *profile.AvatarURL != "" {
		_ = s.avatarStorage.DeleteAvatar(ctx, *profile.AvatarURL)
		profile.AvatarURL = nil
		return s.repo.Save(ctx, profile)
	}

	return profile, nil
}

func (s *UserProfileService) UpdateKycStatus(ctx context.Context, userID uuid.UUID, kycStatus string) (*domain.UserProfile, error) {
	status := domain.KycStatus(kycStatus)
	if !status.IsValid() {
		return nil, fmt.Errorf("invalid KYC status: %s", kycStatus)
	}
	if err := s.repo.UpdateKycStatus(ctx, userID, status); err != nil {
		return nil, err
	}
	log.Printf("[UserProfileService] Updated KYC status for userId=%s to %s", userID, kycStatus)
	return s.repo.FindByID(ctx, userID)
}
