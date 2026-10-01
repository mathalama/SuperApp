package service

import (
	"context"
	"io"
	"testing"

	"dev.mathalama/userservice/internal/domain"
	"dev.mathalama/userservice/internal/dto"
	"dev.mathalama/userservice/internal/repository"
	"github.com/google/uuid"
)

type mockRepo struct {
	profiles map[uuid.UUID]*domain.UserProfile
}

func newMockRepo() *mockRepo {
	return &mockRepo{profiles: make(map[uuid.UUID]*domain.UserProfile)}
}

func (m *mockRepo) ExistsByID(ctx context.Context, id uuid.UUID) (bool, error) {
	_, ok := m.profiles[id]
	return ok, nil
}

func (m *mockRepo) FindByID(ctx context.Context, id uuid.UUID) (*domain.UserProfile, error) {
	p, ok := m.profiles[id]
	if !ok {
		return nil, repository.ErrNotFound
	}
	return p, nil
}

func (m *mockRepo) Save(ctx context.Context, profile *domain.UserProfile) (*domain.UserProfile, error) {
	m.profiles[profile.ID] = profile
	return profile, nil
}

type mockAvatarStorage struct {
	deletedURLs []string
	uploadedURL string
}

func (m *mockAvatarStorage) UploadAvatar(ctx context.Context, userID uuid.UUID, r io.Reader, size int64) (string, error) {
	return m.uploadedURL, nil
}

func (m *mockAvatarStorage) DeleteAvatar(ctx context.Context, avatarURL string) error {
	m.deletedURLs = append(m.deletedURLs, avatarURL)
	return nil
}

func TestUserProfileService_CreateProfile_New(t *testing.T) {
	repo := newMockRepo()
	avatarMock := &mockAvatarStorage{}
	svc := NewUserProfileService(repo, avatarMock)

	userId := uuid.New()
	p, err := svc.CreateProfile(context.Background(), userId, "alice", "alice@example.com")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if p.ID != userId {
		t.Errorf("expected userId %s, got %s", userId, p.ID)
	}
	if p.Username != "alice" || p.Email != "alice@example.com" {
		t.Errorf("unexpected profile: %+v", p)
	}
}

func TestUserProfileService_CreateProfile_AlreadyExists(t *testing.T) {
	repo := newMockRepo()
	avatarMock := &mockAvatarStorage{}
	svc := NewUserProfileService(repo, avatarMock)

	userId := uuid.New()
	existing := domain.NewUserProfile(userId, "existing_user", "exist@example.com")
	repo.profiles[userId] = existing

	p, err := svc.CreateProfile(context.Background(), userId, "different_name", "different@example.com")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if p.Username != "existing_user" {
		t.Errorf("expected existing username to be returned, got %s", p.Username)
	}
}

func TestUserProfileService_UpdateProfile(t *testing.T) {
	repo := newMockRepo()
	avatarMock := &mockAvatarStorage{}
	svc := NewUserProfileService(repo, avatarMock)

	userId := uuid.New()
	existing := domain.NewUserProfile(userId, "bob", "bob@example.com")
	repo.profiles[userId] = existing

	newBio := "Full Stack Developer"
	newPhone := "+77011234567"
	newTz := "Asia/Almaty"
	req := &dto.UpdateProfileRequest{
		Bio:         &newBio,
		PhoneNumber: &newPhone,
		Timezone:    &newTz,
	}

	updated, err := svc.UpdateProfile(context.Background(), userId, req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if updated.Bio == nil || *updated.Bio != "Full Stack Developer" {
		t.Errorf("expected bio to be updated")
	}
	if updated.PhoneNumber == nil || *updated.PhoneNumber != "+77011234567" {
		t.Errorf("expected phone number to be updated")
	}
	if updated.Timezone != "Asia/Almaty" {
		t.Errorf("expected timezone Asia/Almaty, got %s", updated.Timezone)
	}
}

func TestUserProfileService_DeleteAvatar(t *testing.T) {
	repo := newMockRepo()
	avatarMock := &mockAvatarStorage{}
	svc := NewUserProfileService(repo, avatarMock)

	userId := uuid.New()
	existing := domain.NewUserProfile(userId, "carol", "carol@example.com")
	oldAvatar := "http://s3.example.com/avatars/carol.jpg"
	existing.AvatarURL = &oldAvatar
	repo.profiles[userId] = existing

	updated, err := svc.DeleteAvatar(context.Background(), userId)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if updated.AvatarURL != nil {
		t.Errorf("expected AvatarURL to be nil after deletion")
	}
	if len(avatarMock.deletedURLs) != 1 || avatarMock.deletedURLs[0] != oldAvatar {
		t.Errorf("expected S3 avatar to be deleted")
	}
}
