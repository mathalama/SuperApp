package dto

import "time"

type DepositRequest struct {
	Currency       string  `json:"currency"`
	Amount         float64 `json:"amount"`
	IdempotencyKey string  `json:"idempotencyKey,omitempty"`
	Description    string  `json:"description,omitempty"`
}

type TransferRequest struct {
	Recipient      string  `json:"recipient"` // Username, Email, or UUID
	Currency       string  `json:"currency"`
	Amount         float64 `json:"amount"`
	IdempotencyKey string  `json:"idempotencyKey,omitempty"`
	Description    string  `json:"description,omitempty"`
}

type WalletResponse struct {
	ID        string    `json:"id"`
	UserID    string    `json:"userId"`
	Currency  string    `json:"currency"`
	Balance   float64   `json:"balance"`
	Status    string    `json:"status"`
	UpdatedAt time.Time `json:"updatedAt"`
}

type WalletsSummaryResponse struct {
	Wallets        []WalletResponse `json:"wallets"`
	KycStatus      string           `json:"kycStatus"`
	DailyLimit     float64          `json:"dailyLimit"`
	DailySpent     float64          `json:"dailySpent"`
	DailyRemaining float64          `json:"dailyRemaining"`
}

type TransactionResponse struct {
	ID            string     `json:"id"`
	Type          string     `json:"type"`
	Amount        float64    `json:"amount"`
	Currency      string     `json:"currency"`
	Status        string     `json:"status"`
	Description   *string    `json:"description,omitempty"`
	Direction     string     `json:"direction"` // INCOMING, OUTGOING, SELF
	Counterparty  string     `json:"counterparty,omitempty"`
	CreatedAt     time.Time  `json:"createdAt"`
	CompletedAt   *time.Time `json:"completedAt,omitempty"`
}

type PaginatedTransactionsResponse struct {
	Transactions []TransactionResponse `json:"transactions"`
	Total        int                   `json:"total"`
	Limit        int                   `json:"limit"`
	Offset       int                   `json:"offset"`
}
