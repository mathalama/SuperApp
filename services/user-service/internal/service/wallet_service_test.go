package service

import (
	"context"
	"testing"
	"time"

	"dev.mathalama/userservice/internal/domain"
	"dev.mathalama/userservice/internal/dto"
	"github.com/google/uuid"
)

type mockWalletRepo struct {
	wallets      map[string]*domain.Wallet // key: userId.String() + ":" + currency
	transactions []*domain.Transaction
	dailySpent   map[uuid.UUID]float64
}

func newMockWalletRepo() *mockWalletRepo {
	return &mockWalletRepo{
		wallets:    make(map[string]*domain.Wallet),
		dailySpent: make(map[uuid.UUID]float64),
	}
}

func (m *mockWalletRepo) GetWalletsByUserID(ctx context.Context, userID uuid.UUID) ([]*domain.Wallet, error) {
	var res []*domain.Wallet
	for _, w := range m.wallets {
		if w.UserID == userID {
			res = append(res, w)
		}
	}
	return res, nil
}

func (m *mockWalletRepo) GetWalletByUserAndCurrency(ctx context.Context, userID uuid.UUID, currency string) (*domain.Wallet, error) {
	key := userID.String() + ":" + currency
	w, ok := m.wallets[key]
	if !ok {
		return nil, domain.ErrWalletNotFound
	}
	return w, nil
}

func (m *mockWalletRepo) FindOrCreateWallet(ctx context.Context, userID uuid.UUID, currency string) (*domain.Wallet, error) {
	w, err := m.GetWalletByUserAndCurrency(ctx, userID, currency)
	if err == nil {
		return w, nil
	}
	newW := &domain.Wallet{
		ID:        uuid.New(),
		UserID:    userID,
		Currency:  currency,
		Balance:   0.0,
		Status:    domain.WalletStatusActive,
		CreatedAt: time.Now().UTC(),
		UpdatedAt: time.Now().UTC(),
	}
	m.wallets[userID.String()+":"+currency] = newW
	return newW, nil
}

func (m *mockWalletRepo) Deposit(ctx context.Context, userID uuid.UUID, currency string, amount float64, idempotencyKey, description string) (*domain.Transaction, error) {
	w, _ := m.FindOrCreateWallet(ctx, userID, currency)
	w.Balance += amount
	now := time.Now().UTC()
	tx := &domain.Transaction{
		ID:                  uuid.New(),
		DestinationWalletID: &w.ID,
		DestinationUserID:   &userID,
		Type:                domain.TransactionTypeDeposit,
		Amount:              amount,
		Currency:            currency,
		Status:              domain.TransactionStatusCompleted,
		CreatedAt:           now,
		CompletedAt:         &now,
	}
	m.transactions = append(m.transactions, tx)
	return tx, nil
}

func (m *mockWalletRepo) TransferP2P(ctx context.Context, sourceUserID, destUserID uuid.UUID, currency string, amount float64, idempotencyKey, description string) (*domain.Transaction, error) {
	srcW, err := m.GetWalletByUserAndCurrency(ctx, sourceUserID, currency)
	if err != nil {
		return nil, domain.ErrWalletNotFound
	}
	if srcW.Balance < amount {
		return nil, domain.ErrInsufficientFunds
	}
	dstW, _ := m.FindOrCreateWallet(ctx, destUserID, currency)
	srcW.Balance -= amount
	dstW.Balance += amount
	m.dailySpent[sourceUserID] += amount
	now := time.Now().UTC()
	tx := &domain.Transaction{
		ID:                  uuid.New(),
		SourceWalletID:      &srcW.ID,
		DestinationWalletID: &dstW.ID,
		SourceUserID:        &sourceUserID,
		DestinationUserID:   &destUserID,
		Type:                domain.TransactionTypeTransfer,
		Amount:              amount,
		Currency:            currency,
		Status:              domain.TransactionStatusCompleted,
		CreatedAt:           now,
		CompletedAt:         &now,
	}
	m.transactions = append(m.transactions, tx)
	return tx, nil
}

func (m *mockWalletRepo) GetDailySpentAmount(ctx context.Context, userID uuid.UUID) (float64, error) {
	return m.dailySpent[userID], nil
}

func (m *mockWalletRepo) GetTransactionsByUserID(ctx context.Context, userID uuid.UUID, limit, offset int) ([]*domain.Transaction, int, error) {
	return m.transactions, len(m.transactions), nil
}

func TestWalletService_KycEnforcementAndTransfer(t *testing.T) {
	userRepo := newMockRepo()
	walletRepo := newMockWalletRepo()
	svc := NewWalletService(walletRepo, userRepo)

	// User 1: Unverified (NOT_STARTED)
	u1 := uuid.New()
	p1 := domain.NewUserProfile(u1, "unverified_bob", "bob@example.com")
	p1.KycStatus = domain.KycStatusNotStarted
	userRepo.profiles[u1] = p1

	// User 2: Verified (VERIFIED)
	u2 := uuid.New()
	p2 := domain.NewUserProfile(u2, "verified_alice", "alice@example.com")
	p2.KycStatus = domain.KycStatusVerified
	userRepo.profiles[u2] = p2

	// Deposit $1000 into Alice's wallet
	_, err := svc.Deposit(context.Background(), u2, dto.DepositRequest{
		Currency: "USD",
		Amount:   1000.0,
	})
	if err != nil {
		t.Fatalf("failed deposit to alice: %v", err)
	}

	// 1. Unverified Bob tries to transfer: must fail with KYC required
	_, err = svc.Transfer(context.Background(), u1, dto.TransferRequest{
		Recipient: "alice@example.com",
		Currency:  "USD",
		Amount:    50.0,
	})
	if err != domain.ErrKycRequired {
		t.Errorf("expected ErrKycRequired for unverified bob, got %v", err)
	}

	// 2. Verified Alice transfers $250 to Bob by username: must succeed
	tx, err := svc.Transfer(context.Background(), u2, dto.TransferRequest{
		Recipient: "unverified_bob",
		Currency:  "USD",
		Amount:    250.0,
	})
	if err != nil {
		t.Fatalf("alice transfer should succeed: %v", err)
	}
	if tx.Amount != 250.0 {
		t.Errorf("expected amount 250, got %f", tx.Amount)
	}

	// Verify Bob's received balance
	bobSummary, err := svc.GetWalletsSummary(context.Background(), u1)
	if err != nil {
		t.Fatalf("failed get bob summary: %v", err)
	}
	if len(bobSummary.Wallets) == 0 || bobSummary.Wallets[0].Balance != 250.0 {
		t.Errorf("expected Bob to have 250 USD, got %+v", bobSummary.Wallets)
	}

	// Verify Alice's remaining limit
	aliceSummary, err := svc.GetWalletsSummary(context.Background(), u2)
	if err != nil {
		t.Fatalf("failed get alice summary: %v", err)
	}
	if aliceSummary.DailySpent != 250.0 {
		t.Errorf("expected Alice daily spent 250, got %f", aliceSummary.DailySpent)
	}
	if aliceSummary.DailyRemaining != 49750.0 {
		t.Errorf("expected Alice daily remaining 49750, got %f", aliceSummary.DailyRemaining)
	}
}
