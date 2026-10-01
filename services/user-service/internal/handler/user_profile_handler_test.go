package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"dev.mathalama/userservice/internal/domain"
	"dev.mathalama/userservice/internal/dto"
	"dev.mathalama/userservice/internal/repository"
	"dev.mathalama/userservice/internal/service"
	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
)

type handlerMockRepo struct {
	profiles map[uuid.UUID]*domain.UserProfile
}

func (m *handlerMockRepo) ExistsByID(ctx context.Context, id uuid.UUID) (bool, error) {
	_, ok := m.profiles[id]
	return ok, nil
}

func (m *handlerMockRepo) FindByID(ctx context.Context, id uuid.UUID) (*domain.UserProfile, error) {
	p, ok := m.profiles[id]
	if !ok {
		return nil, repository.ErrNotFound
	}
	return p, nil
}

func (m *handlerMockRepo) Save(ctx context.Context, profile *domain.UserProfile) (*domain.UserProfile, error) {
	m.profiles[profile.ID] = profile
	return profile, nil
}

type handlerMockAvatarStorage struct{}

func (m *handlerMockAvatarStorage) UploadAvatar(ctx context.Context, userID uuid.UUID, r io.Reader, size int64) (string, error) {
	return "http://localhost:8504/avatars/" + userID.String() + ".jpg", nil
}

func (m *handlerMockAvatarStorage) DeleteAvatar(ctx context.Context, avatarURL string) error {
	return nil
}

func setupTestRouter() (chi.Router, *handlerMockRepo) {
	repo := &handlerMockRepo{profiles: make(map[uuid.UUID]*domain.UserProfile)}
	storageMock := &handlerMockAvatarStorage{}
	svc := service.NewUserProfileService(repo, storageMock)
	h := NewUserProfileHandler(svc)

	r := chi.NewRouter()
	h.RegisterRoutes(r)
	return r, repo
}

func TestGetMyProfile_MissingHeader(t *testing.T) {
	r, _ := setupTestRouter()

	req := httptest.NewRequest("GET", "/api/users/me", nil)
	w := httptest.NewRecorder()

	r.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("expected 401 Unauthorized for missing X-User-Id header, got %d", w.Code)
	}
}

func TestGetMyProfile_Found(t *testing.T) {
	r, repo := setupTestRouter()

	userId := uuid.New()
	p := domain.NewUserProfile(userId, "testuser", "test@example.com")
	repo.profiles[userId] = p

	req := httptest.NewRequest("GET", "/api/users/me", nil)
	req.Header.Set("X-User-Id", userId.String())
	w := httptest.NewRecorder()

	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d: %s", w.Code, w.Body.String())
	}

	var resp dto.UserProfileResponse
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("failed decoding response: %v", err)
	}

	if resp.ID != userId || resp.Username != "testuser" {
		t.Errorf("unexpected profile in response: %+v", resp)
	}
}

func TestGetMyProfile_NotFound(t *testing.T) {
	r, _ := setupTestRouter()

	userId := uuid.New()
	req := httptest.NewRequest("GET", "/api/users/me", nil)
	req.Header.Set("X-User-Id", userId.String())
	w := httptest.NewRecorder()

	r.ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("expected 404 Not Found, got %d", w.Code)
	}
}

func TestUpdateMyProfile_Success(t *testing.T) {
	r, repo := setupTestRouter()

	userId := uuid.New()
	p := domain.NewUserProfile(userId, "bob", "bob@example.com")
	repo.profiles[userId] = p

	bio := "Updated Bio"
	updateReq := dto.UpdateProfileRequest{
		Bio: &bio,
	}
	body, _ := json.Marshal(updateReq)

	req := httptest.NewRequest("PUT", "/api/users/me", bytes.NewReader(body))
	req.Header.Set("X-User-Id", userId.String())
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d: %s", w.Code, w.Body.String())
	}

	var resp dto.UserProfileResponse
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("failed decoding response: %v", err)
	}

	if resp.Bio == nil || *resp.Bio != "Updated Bio" {
		t.Errorf("expected bio to be updated")
	}
}

func TestHealthCheck(t *testing.T) {
	r, _ := setupTestRouter()

	req := httptest.NewRequest("GET", "/actuator/health", nil)
	w := httptest.NewRecorder()

	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200 OK, got %d", w.Code)
	}
}
