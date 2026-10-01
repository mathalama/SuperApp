package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const testSecret = "test-secret-at-least-256-bits-long-random-string-for-testing"

func createTestToken(t *testing.T, secret string, tokenType string, userId string, roles []string, jti string, expired bool) string {
	exp := time.Now().Add(1 * time.Hour)
	if expired {
		exp = time.Now().Add(-1 * time.Hour)
	}

	claims := CustomClaims{
		Type:  tokenType,
		Roles: roles,
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   userId,
			ID:        jti,
			ExpiresAt: jwt.NewNumericDate(exp),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenStr, err := token.SignedString([]byte(secret))
	if err != nil {
		t.Fatalf("Failed to sign token: %v", err)
	}
	return tokenStr
}

func TestPublicRoutesBypassJwt(t *testing.T) {
	mw := NewJwtRelayMiddleware(testSecret, nil)

	var recordedUserId string
	var recordedRoles string

	dummyHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		recordedUserId = r.Header.Get("X-User-Id")
		recordedRoles = r.Header.Get("X-User-Roles")
		w.WriteHeader(http.StatusOK)
	})

	handler := mw.Handler(dummyHandler)

	req := httptest.NewRequest(http.MethodPost, "/auth/register", nil)
	// Spoofed client headers
	req.Header.Set("X-User-Id", "attacker-id")
	req.Header.Set("X-User-Roles", "ROLE_ADMIN")

	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("Expected 200 OK for public route, got %d", rec.Code)
	}
	if recordedUserId != "" {
		t.Errorf("Expected X-User-Id to be stripped, got %q", recordedUserId)
	}
	if recordedRoles != "" {
		t.Errorf("Expected X-User-Roles to be stripped, got %q", recordedRoles)
	}
}

func TestMissingAuthHeaderReturns401(t *testing.T) {
	mw := NewJwtRelayMiddleware(testSecret, nil)
	handler := mw.Handler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest(http.MethodGet, "/api/users/profile", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("Expected 401 Unauthorized, got %d", rec.Code)
	}
}

func TestInvalidTokenReturns401(t *testing.T) {
	mw := NewJwtRelayMiddleware(testSecret, nil)
	handler := mw.Handler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest(http.MethodGet, "/api/users/profile", nil)
	req.Header.Set("Authorization", "Bearer invalid.token.garbage")
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("Expected 401 Unauthorized, got %d", rec.Code)
	}
}

func TestWrongTokenTypeReturns401(t *testing.T) {
	mw := NewJwtRelayMiddleware(testSecret, nil)
	handler := mw.Handler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	refreshToken := createTestToken(t, testSecret, "refresh", "user-123", []string{"ROLE_USER"}, "jti-1", false)

	req := httptest.NewRequest(http.MethodGet, "/api/users/profile", nil)
	req.Header.Set("Authorization", "Bearer "+refreshToken)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("Expected 401 Unauthorized for refresh token, got %d", rec.Code)
	}
}

func TestValidAccessTokenInjectsHeaders(t *testing.T) {
	mw := NewJwtRelayMiddleware(testSecret, nil)

	var recordedUserId string
	var recordedRoles string

	handler := mw.Handler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		recordedUserId = r.Header.Get("X-User-Id")
		recordedRoles = r.Header.Get("X-User-Roles")
		w.WriteHeader(http.StatusOK)
	}))

	accessToken := createTestToken(t, testSecret, "access", "user-uuid-999", []string{"ROLE_USER", "ROLE_ADMIN"}, "jti-access-1", false)

	req := httptest.NewRequest(http.MethodGet, "/api/users/me", nil)
	req.Header.Set("Authorization", "Bearer "+accessToken)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("Expected 200 OK, got %d", rec.Code)
	}
	if recordedUserId != "user-uuid-999" {
		t.Errorf("Expected X-User-Id 'user-uuid-999', got %q", recordedUserId)
	}
	if recordedRoles != "ROLE_USER,ROLE_ADMIN" {
		t.Errorf("Expected X-User-Roles 'ROLE_USER,ROLE_ADMIN', got %q", recordedRoles)
	}
}
