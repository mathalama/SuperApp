package service

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"dev.mathalama/userservice/internal/domain"
	"dev.mathalama/userservice/internal/dto"
	"dev.mathalama/userservice/internal/repository"
	"github.com/google/uuid"
)

type WalletService struct {
	walletRepo repository.WalletRepository
	userRepo   repository.UserProfileRepository
}

func NewWalletService(
	walletRepo repository.WalletRepository,
	userRepo repository.UserProfileRepository,
) *WalletService {
	return &WalletService{
		walletRepo: walletRepo,
		userRepo:   userRepo,
	}
}

func (s *WalletService) GetWalletsSummary(ctx context.Context, userID uuid.UUID) (*dto.WalletsSummaryResponse, error) {
	profile, err := s.userRepo.FindByID(ctx, userID)
	if err != nil && !errors.Is(err, repository.ErrNotFound) {
		return nil, fmt.Errorf("find user profile: %w", err)
	}

	kycStatus := domain.KycStatusNotStarted
	if profile != nil && profile.KycStatus != "" {
		kycStatus = profile.KycStatus
	}

	wallets, err := s.walletRepo.GetWalletsByUserID(ctx, userID)
	if err != nil {
		return nil, fmt.Errorf("get wallets: %w", err)
	}

	// Auto-provision default USD wallet if user has none
	if len(wallets) == 0 {
		defaultWallet, err := s.walletRepo.FindOrCreateWallet(ctx, userID, "USD")
		if err == nil {
			wallets = append(wallets, defaultWallet)
		}
	}

	dailySpent, err := s.walletRepo.GetDailySpentAmount(ctx, userID)
	if err != nil {
		dailySpent = 0.0
	}

	dailyLimit := domain.GetDailyTransferLimit(kycStatus)
	dailyRemaining := dailyLimit - dailySpent
	if dailyRemaining < 0 {
		dailyRemaining = 0
	}

	var responses []dto.WalletResponse
	for _, w := range wallets {
		responses = append(responses, dto.WalletResponse{
			ID:        w.ID.String(),
			UserID:    w.UserID.String(),
			Currency:  w.Currency,
			Balance:   w.Balance,
			Status:    string(w.Status),
			UpdatedAt: w.UpdatedAt,
		})
	}

	return &dto.WalletsSummaryResponse{
		Wallets:        responses,
		KycStatus:      string(kycStatus),
		DailyLimit:     dailyLimit,
		DailySpent:     dailySpent,
		DailyRemaining: dailyRemaining,
	}, nil
}

func (s *WalletService) Deposit(ctx context.Context, userID uuid.UUID, req dto.DepositRequest) (*domain.Transaction, error) {
	currency := strings.ToUpper(strings.TrimSpace(req.Currency))
	if currency == "" {
		currency = "USD"
	}
	if req.Amount <= 0 {
		return nil, domain.ErrInvalidAmount
	}

	return s.walletRepo.Deposit(ctx, userID, currency, req.Amount, req.IdempotencyKey, req.Description)
}

func (s *WalletService) Transfer(ctx context.Context, senderID uuid.UUID, req dto.TransferRequest) (*domain.Transaction, error) {
	currency := strings.ToUpper(strings.TrimSpace(req.Currency))
	if currency == "" {
		currency = "USD"
	}
	if req.Amount <= 0 {
		return nil, domain.ErrInvalidAmount
	}

	// 1. Check sender's KYC limits
	senderProfile, err := s.userRepo.FindByID(ctx, senderID)
	if err != nil {
		return nil, fmt.Errorf("sender profile not found: %w", err)
	}

	dailySpent, err := s.walletRepo.GetDailySpentAmount(ctx, senderID)
	if err != nil {
		return nil, fmt.Errorf("get daily spent: %w", err)
	}

	if err := domain.CheckTransferLimit(senderProfile.KycStatus, dailySpent, req.Amount); err != nil {
		return nil, err
	}

	// 2. Resolve recipient (by UUID, Username, or Email)
	recipientTarget := strings.TrimSpace(req.Recipient)
	if recipientTarget == "" {
		return nil, errors.New("recipient must be specified (username, email, or user ID)")
	}

	var recipientID uuid.UUID
	if parsedUUID, err := uuid.Parse(recipientTarget); err == nil {
		exists, err := s.userRepo.ExistsByID(ctx, parsedUUID)
		if err != nil || !exists {
			return nil, fmt.Errorf("recipient user with ID %s not found", recipientTarget)
		}
		recipientID = parsedUUID
	} else {
		// Try finding by username
		recProfile, err := s.userRepo.FindByUsername(ctx, recipientTarget)
		if err == nil {
			recipientID = recProfile.ID
		} else {
			// Try finding by email
			recProfile, err := s.userRepo.FindByEmail(ctx, recipientTarget)
			if err == nil {
				recipientID = recProfile.ID
			} else {
				return nil, fmt.Errorf("recipient '%s' not found by username or email", recipientTarget)
			}
		}
	}

	if senderID == recipientID {
		return nil, domain.ErrSameAccountTransfer
	}

	// 3. Execute atomic transfer
	return s.walletRepo.TransferP2P(ctx, senderID, recipientID, currency, req.Amount, req.IdempotencyKey, req.Description)
}

func (s *WalletService) GetTransactions(ctx context.Context, userID uuid.UUID, limit, offset int) (*dto.PaginatedTransactionsResponse, error) {
	if limit <= 0 || limit > 100 {
		limit = 20
	}
	if offset < 0 {
		offset = 0
	}

	transactions, total, err := s.walletRepo.GetTransactionsByUserID(ctx, userID, limit, offset)
	if err != nil {
		return nil, fmt.Errorf("get transactions: %w", err)
	}

	var responses []dto.TransactionResponse
	for _, tx := range transactions {
		direction := "SELF"
		counterparty := ""

		if tx.SourceUserID != nil && *tx.SourceUserID == userID {
			direction = "OUTGOING"
			if tx.DestinationUserID != nil {
				counterparty = tx.DestinationUserID.String()
			}
		} else if tx.DestinationUserID != nil && *tx.DestinationUserID == userID {
			direction = "INCOMING"
			if tx.SourceUserID != nil {
				counterparty = tx.SourceUserID.String()
			}
		}

		responses = append(responses, dto.TransactionResponse{
			ID:           tx.ID.String(),
			Type:         string(tx.Type),
			Amount:       tx.Amount,
			Currency:     tx.Currency,
			Status:       string(tx.Status),
			Description:  tx.Description,
			Direction:    direction,
			Counterparty: counterparty,
			CreatedAt:    tx.CreatedAt,
			CompletedAt:  tx.CompletedAt,
		})
	}

	return &dto.PaginatedTransactionsResponse{
		Transactions: responses,
		Total:        total,
		Limit:        limit,
		Offset:       offset,
	}, nil
}
