package handler

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"dev.mathalama/userservice/internal/dto"
	"dev.mathalama/userservice/internal/repository"
	"dev.mathalama/userservice/internal/service"
	"dev.mathalama/userservice/internal/storage"
	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
)

type UserProfileHandler struct {
	svc *service.UserProfileService
}

func NewUserProfileHandler(svc *service.UserProfileService) *UserProfileHandler {
	return &UserProfileHandler{svc: svc}
}

func (h *UserProfileHandler) RegisterRoutes(r chi.Router) {
	// API routes
	r.Route("/api/users", func(r chi.Router) {
		r.Get("/me", h.GetMyProfile)
		r.Put("/me", h.UpdateMyProfile)
		r.Post("/me/avatar", h.UploadAvatar)
		r.Delete("/me/avatar", h.DeleteAvatar)
		r.Get("/{id}", h.GetProfileByID)
	})

	// Health and actuator endpoints
	r.Get("/actuator/health", h.HealthCheck)
	r.Get("/health", h.HealthCheck)
	r.Get("/actuator/info", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]string{
			"app":     "user-service",
			"status":  "running",
			"runtime": "go",
		})
	})

	// Swagger / OpenAPI documentation stub for Gateway compatibility
	r.Get("/user/v3/api-docs", h.ApiDocs)
	r.Get("/v3/api-docs", h.ApiDocs)
}

func (h *UserProfileHandler) GetMyProfile(w http.ResponseWriter, r *http.Request) {
	userIDStr := r.Header.Get("X-User-Id")
	if userIDStr == "" {
		writeError(w, http.StatusUnauthorized, "Missing authentication header")
		return
	}

	userID, err := uuid.Parse(userIDStr)
	if err != nil {
		writeError(w, http.StatusBadRequest, "Invalid X-User-Id header format")
		return
	}

	profile, err := h.svc.GetProfile(r.Context(), userID)
	if err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			writeError(w, http.StatusNotFound, fmt.Sprintf("Profile not found for userId=%s", userID))
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, dto.FromDomain(profile))
}

func (h *UserProfileHandler) UpdateMyProfile(w http.ResponseWriter, r *http.Request) {
	userIDStr := r.Header.Get("X-User-Id")
	if userIDStr == "" {
		writeError(w, http.StatusUnauthorized, "Missing authentication header")
		return
	}

	userID, err := uuid.Parse(userIDStr)
	if err != nil {
		writeError(w, http.StatusBadRequest, "Invalid X-User-Id header format")
		return
	}

	var req dto.UpdateProfileRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "Invalid request body")
		return
	}

	if err := req.Validate(); err != nil {
		writeValidationError(w, err.Error())
		return
	}

	updated, err := h.svc.UpdateProfile(r.Context(), userID, &req)
	if err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			writeError(w, http.StatusNotFound, fmt.Sprintf("Profile not found for userId=%s", userID))
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, dto.FromDomain(updated))
}

func (h *UserProfileHandler) GetProfileByID(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := uuid.Parse(idStr)
	if err != nil {
		writeError(w, http.StatusBadRequest, "Invalid user id format")
		return
	}

	profile, err := h.svc.GetProfile(r.Context(), id)
	if err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			writeError(w, http.StatusNotFound, fmt.Sprintf("Profile not found for id=%s", id))
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, dto.FromDomain(profile))
}

func (h *UserProfileHandler) UploadAvatar(w http.ResponseWriter, r *http.Request) {
	userIDStr := r.Header.Get("X-User-Id")
	if userIDStr == "" {
		writeError(w, http.StatusUnauthorized, "Missing authentication header")
		return
	}

	userID, err := uuid.Parse(userIDStr)
	if err != nil {
		writeError(w, http.StatusBadRequest, "Invalid X-User-Id header format")
		return
	}

	// 5MB limit max parsing
	if err := r.ParseMultipartForm(storage.MaxFileSize); err != nil {
		if strings.Contains(err.Error(), "too large") {
			writePayloadTooLarge(w)
			return
		}
	}

	file, header, err := r.FormFile("file")
	if err != nil {
		writeError(w, http.StatusBadRequest, "Avatar file cannot be empty")
		return
	}
	defer file.Close()

	if header.Size > storage.MaxFileSize {
		writePayloadTooLarge(w)
		return
	}

	updated, err := h.svc.UploadAvatar(r.Context(), userID, file, header.Size)
	if err != nil {
		if errors.Is(err, storage.ErrFileTooLarge) {
			writePayloadTooLarge(w)
			return
		}
		if errors.Is(err, storage.ErrEmptyFile) || errors.Is(err, storage.ErrUnsupportedImage) ||
			strings.Contains(err.Error(), "Image dimensions too large") {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		if errors.Is(err, repository.ErrNotFound) {
			writeError(w, http.StatusNotFound, fmt.Sprintf("Profile not found for userId=%s", userID))
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, dto.FromDomain(updated))
}

func (h *UserProfileHandler) DeleteAvatar(w http.ResponseWriter, r *http.Request) {
	userIDStr := r.Header.Get("X-User-Id")
	if userIDStr == "" {
		writeError(w, http.StatusUnauthorized, "Missing authentication header")
		return
	}

	userID, err := uuid.Parse(userIDStr)
	if err != nil {
		writeError(w, http.StatusBadRequest, "Invalid X-User-Id header format")
		return
	}

	updated, err := h.svc.DeleteAvatar(r.Context(), userID)
	if err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			writeError(w, http.StatusNotFound, fmt.Sprintf("Profile not found for userId=%s", userID))
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, dto.FromDomain(updated))
}

func (h *UserProfileHandler) HealthCheck(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status": "UP",
		"components": map[string]interface{}{
			"db":    map[string]string{"status": "UP"},
			"minio": map[string]string{"status": "UP"},
		},
	})
}

func (h *UserProfileHandler) ApiDocs(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"openapi": "3.0.1",
		"info": map[string]string{
			"title":   "User Service API",
			"version": "1.0.0",
		},
		"paths": map[string]interface{}{
			"/api/users/me": map[string]interface{}{
				"get": map[string]string{"summary": "Get current user profile"},
				"put": map[string]string{"summary": "Update current user profile"},
			},
			"/api/users/{id}": map[string]interface{}{
				"get": map[string]string{"summary": "Get profile by ID"},
			},
			"/api/users/me/avatar": map[string]interface{}{
				"post":   map[string]string{"summary": "Upload avatar"},
				"delete": map[string]string{"summary": "Delete avatar"},
			},
		},
	})
}

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, dto.ErrorResponse{
		Timestamp: time.Now().Format(time.RFC3339),
		Status:    status,
		Error:     msg,
		Message:   msg,
	})
}

func writeValidationError(w http.ResponseWriter, msg string) {
	writeJSON(w, http.StatusBadRequest, dto.ErrorResponse{
		Timestamp: time.Now().Format(time.RFC3339),
		Status:    http.StatusBadRequest,
		Error:     "Validation failed",
		Message:   msg,
		Details:   map[string]string{"request": msg},
	})
}

func writePayloadTooLarge(w http.ResponseWriter) {
	writeJSON(w, http.StatusRequestEntityTooLarge, map[string]interface{}{
		"status":    413,
		"error":     "Payload Too Large",
		"message":   "Avatar file size exceeds maximum limit of 5MB",
		"timestamp": time.Now().UnixMilli(),
	})
}
