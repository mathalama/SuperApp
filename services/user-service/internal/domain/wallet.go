package domain

import (
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
)

var (
	ErrInsufficientFunds   = errors.New("insufficient funds for transfer")
	ErrWalletNotFound      = errors.New("wallet not found")
	ErrWalletFrozen        = errors.New("wallet is frozen or closed")
	ErrKycRequired         = errors.New("KYC verification required: unverified users cannot perform transfers")
	ErrDailyLimitExceeded  = errors.New("daily transfer limit exceeded for current KYC tier")
	ErrSameAccountTransfer = errors.New("cannot transfer funds to the same account")
	ErrInvalidAmount       = errors.New("amount must be greater than zero")
)

type WalletStatus string

const (
	WalletStatusActive WalletStatus = "ACTIVE"
	WalletStatusFrozen WalletStatus = "FROZEN"
	WalletStatusClosed WalletStatus = "CLOSED"
)

type TransactionType string

const (
	TransactionTypeDeposit    TransactionType = "DEPOSIT"
	TransactionTypeWithdrawal TransactionType = "WITHDRAWAL"
	TransactionTypeTransfer   TransactionType = "TRANSFER_P2P"
)

type TransactionStatus string

const (
	TransactionStatusPending   TransactionStatus = "PENDING"
	TransactionStatusCompleted TransactionStatus = "COMPLETED"
	TransactionStatusFailed    TransactionStatus = "FAILED"
	TransactionStatusCancelled TransactionStatus = "CANCELLED"
)

type Wallet struct {
	ID        uuid.UUID    `json:"id"`
	UserID    uuid.UUID    `json:"userId"`
	Currency  string       `json:"currency"`
	Balance   float64      `json:"balance"`
	Status    WalletStatus `json:"status"`
	CreatedAt time.Time    `json:"createdAt"`
	UpdatedAt time.Time    `json:"updatedAt"`
}

type Transaction struct {
	ID                  uuid.UUID         `json:"id"`
	IdempotencyKey      *string           `json:"idempotencyKey,omitempty"`
	SourceWalletID      *uuid.UUID        `json:"sourceWalletId,omitempty"`
	DestinationWalletID *uuid.UUID        `json:"destinationWalletId,omitempty"`
	SourceUserID        *uuid.UUID        `json:"sourceUserId,omitempty"`
	DestinationUserID   *uuid.UUID        `json:"destinationUserId,omitempty"`
	Type                TransactionType   `json:"type"`
	Amount              float64           `json:"amount"`
	Currency            string            `json:"currency"`
	Status              TransactionStatus `json:"status"`
	Description         *string           `json:"description,omitempty"`
	FailureReason       *string           `json:"failureReason,omitempty"`
	CreatedAt           time.Time         `json:"createdAt"`
	CompletedAt         *time.Time        `json:"completedAt,omitempty"`
}

// GetDailyTransferLimit returns the daily limit in USD/equivalent based on KYC status
func GetDailyTransferLimit(status KycStatus) float64 {
	switch status {
	case KycStatusVerified:
		return 50000.0 // Fully verified tier: $50,000 / day
	case KycStatusPending, KycStatusManualReview:
		return 100.0 // Limited tier: $100 / day
	default:
		return 0.0 // Unverified (NOT_STARTED or REJECTED): transfers forbidden
	}
}

// CheckTransferLimit validates if the user can perform a transfer of this amount
func CheckTransferLimit(status KycStatus, spentToday, amount float64) error {
	limit := GetDailyTransferLimit(status)
	if limit <= 0.0 {
		return ErrKycRequired
	}
	if spentToday+amount > limit {
		remaining := limit - spentToday
		if remaining < 0 {
			remaining = 0
		}
		return fmt.Errorf("%w: daily limit is $%.2f, remaining today is $%.2f, requested $%.2f",
			ErrDailyLimitExceeded, limit, remaining, amount)
	}
	return nil
}
