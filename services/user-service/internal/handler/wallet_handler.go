package handler

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"

	"dev.mathalama/userservice/internal/domain"
	"dev.mathalama/userservice/internal/dto"
	"dev.mathalama/userservice/internal/service"
	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
)

type WalletHandler struct {
	svc *service.WalletService
}

func NewWalletHandler(svc *service.WalletService) *WalletHandler {
	return &WalletHandler{svc: svc}
}

func (h *WalletHandler) RegisterRoutes(r chi.Router) {
	r.Get("/api/wallets/me", h.GetMyWallets)
	r.Post("/api/wallets/deposit", h.Deposit)
	r.Post("/api/wallets/transfer", h.Transfer)
	r.Get("/api/wallets/transactions", h.GetTransactions)
}

func (h *WalletHandler) GetMyWallets(w http.ResponseWriter, r *http.Request) {
	userIdStr := r.Header.Get("X-User-Id")
	if userIdStr == "" {
		http.Error(w, `{"error":"UNAUTHORIZED","message":"Missing X-User-Id header"}`, http.StatusUnauthorized)
		return
	}

	userId, err := uuid.Parse(userIdStr)
	if err != nil {
		http.Error(w, `{"error":"BAD_REQUEST","message":"Invalid X-User-Id UUID"}`, http.StatusBadRequest)
		return
	}

	summary, err := h.svc.GetWalletsSummary(r.Context(), userId)
	if err != nil {
		http.Error(w, `{"error":"INTERNAL_ERROR","message":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(summary)
}

func (h *WalletHandler) Deposit(w http.ResponseWriter, r *http.Request) {
	userIdStr := r.Header.Get("X-User-Id")
	if userIdStr == "" {
		http.Error(w, `{"error":"UNAUTHORIZED","message":"Missing X-User-Id header"}`, http.StatusUnauthorized)
		return
	}

	userId, err := uuid.Parse(userIdStr)
	if err != nil {
		http.Error(w, `{"error":"BAD_REQUEST","message":"Invalid X-User-Id UUID"}`, http.StatusBadRequest)
		return
	}

	var req dto.DepositRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"error":"BAD_REQUEST","message":"Invalid request body"}`, http.StatusBadRequest)
		return
	}

	tx, err := h.svc.Deposit(r.Context(), userId, req)
	if err != nil {
		if errors.Is(err, domain.ErrInvalidAmount) {
			http.Error(w, `{"error":"INVALID_AMOUNT","message":"Amount must be greater than zero"}`, http.StatusBadRequest)
			return
		}
		if errors.Is(err, domain.ErrWalletFrozen) {
			http.Error(w, `{"error":"WALLET_FROZEN","message":"Wallet is frozen or inactive"}`, http.StatusBadRequest)
			return
		}
		http.Error(w, `{"error":"DEPOSIT_FAILED","message":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	_ = json.NewEncoder(w).Encode(tx)
}

func (h *WalletHandler) Transfer(w http.ResponseWriter, r *http.Request) {
	userIdStr := r.Header.Get("X-User-Id")
	if userIdStr == "" {
		http.Error(w, `{"error":"UNAUTHORIZED","message":"Missing X-User-Id header"}`, http.StatusUnauthorized)
		return
	}

	userId, err := uuid.Parse(userIdStr)
	if err != nil {
		http.Error(w, `{"error":"BAD_REQUEST","message":"Invalid X-User-Id UUID"}`, http.StatusBadRequest)
		return
	}

	var req dto.TransferRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"error":"BAD_REQUEST","message":"Invalid request body"}`, http.StatusBadRequest)
		return
	}

	tx, err := h.svc.Transfer(r.Context(), userId, req)
	if err != nil {
		if errors.Is(err, domain.ErrKycRequired) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{
				"error":   "KYC_REQUIRED",
				"message": "Identity verification (KYC) is required before performing transfers. Please complete verification in the portal.",
			})
			return
		}
		if errors.Is(err, domain.ErrDailyLimitExceeded) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{
				"error":   "LIMIT_EXCEEDED",
				"message": err.Error(),
			})
			return
		}
		if errors.Is(err, domain.ErrInsufficientFunds) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusBadRequest)
			_ = json.NewEncoder(w).Encode(map[string]string{
				"error":   "INSUFFICIENT_FUNDS",
				"message": "Your wallet does not have sufficient balance for this transfer.",
			})
			return
		}
		if errors.Is(err, domain.ErrSameAccountTransfer) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusBadRequest)
			_ = json.NewEncoder(w).Encode(map[string]string{
				"error":   "INVALID_RECIPIENT",
				"message": "Cannot send transfer to your own account.",
			})
			return
		}
		if errors.Is(err, domain.ErrInvalidAmount) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusBadRequest)
			_ = json.NewEncoder(w).Encode(map[string]string{
				"error":   "INVALID_AMOUNT",
				"message": "Transfer amount must be greater than zero.",
			})
			return
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		_ = json.NewEncoder(w).Encode(map[string]string{
			"error":   "TRANSFER_FAILED",
			"message": err.Error(),
		})
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(tx)
}

func (h *WalletHandler) GetTransactions(w http.ResponseWriter, r *http.Request) {
	userIdStr := r.Header.Get("X-User-Id")
	if userIdStr == "" {
		http.Error(w, `{"error":"UNAUTHORIZED","message":"Missing X-User-Id header"}`, http.StatusUnauthorized)
		return
	}

	userId, err := uuid.Parse(userIdStr)
	if err != nil {
		http.Error(w, `{"error":"BAD_REQUEST","message":"Invalid X-User-Id UUID"}`, http.StatusBadRequest)
		return
	}

	limit := 20
	if l := r.URL.Query().Get("limit"); l != "" {
		if val, err := strconv.Atoi(l); err == nil && val > 0 {
			limit = val
		}
	}

	offset := 0
	if o := r.URL.Query().Get("offset"); o != "" {
		if val, err := strconv.Atoi(o); err == nil && val >= 0 {
			offset = val
		}
	}

	resp, err := h.svc.GetTransactions(r.Context(), userId, limit, offset)
	if err != nil {
		http.Error(w, `{"error":"INTERNAL_ERROR","message":"`+err.Error()+`"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(resp)
}
