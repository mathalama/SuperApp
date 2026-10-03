package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"dev.mathalama/userservice/internal/domain"
	"github.com/google/uuid"
)

type WalletRepository interface {
	GetWalletsByUserID(ctx context.Context, userID uuid.UUID) ([]*domain.Wallet, error)
	GetWalletByUserAndCurrency(ctx context.Context, userID uuid.UUID, currency string) (*domain.Wallet, error)
	FindOrCreateWallet(ctx context.Context, userID uuid.UUID, currency string) (*domain.Wallet, error)
	Deposit(ctx context.Context, userID uuid.UUID, currency string, amount float64, idempotencyKey, description string) (*domain.Transaction, error)
	TransferP2P(ctx context.Context, sourceUserID, destUserID uuid.UUID, currency string, amount float64, idempotencyKey, description string) (*domain.Transaction, error)
	GetDailySpentAmount(ctx context.Context, userID uuid.UUID) (float64, error)
	GetTransactionsByUserID(ctx context.Context, userID uuid.UUID, limit, offset int) ([]*domain.Transaction, int, error)
}

type PostgresWalletRepository struct {
	db *sql.DB
}

func NewPostgresWalletRepository(db *sql.DB) *PostgresWalletRepository {
	return &PostgresWalletRepository{db: db}
}

func (r *PostgresWalletRepository) GetWalletsByUserID(ctx context.Context, userID uuid.UUID) ([]*domain.Wallet, error) {
	query := `SELECT id, user_id, currency, balance, status, created_at, updated_at
	          FROM wallets WHERE user_id = $1 ORDER BY currency ASC`

	rows, err := r.db.QueryContext(ctx, query, userID)
	if err != nil {
		return nil, fmt.Errorf("query wallets: %w", err)
	}
	defer rows.Close()

	var wallets []*domain.Wallet
	for rows.Next() {
		w := &domain.Wallet{}
		if err := rows.Scan(&w.ID, &w.UserID, &w.Currency, &w.Balance, &w.Status, &w.CreatedAt, &w.UpdatedAt); err != nil {
			return nil, fmt.Errorf("scan wallet: %w", err)
		}
		wallets = append(wallets, w)
	}
	return wallets, nil
}

func (r *PostgresWalletRepository) GetWalletByUserAndCurrency(ctx context.Context, userID uuid.UUID, currency string) (*domain.Wallet, error) {
	query := `SELECT id, user_id, currency, balance, status, created_at, updated_at
	          FROM wallets WHERE user_id = $1 AND currency = $2`

	w := &domain.Wallet{}
	err := r.db.QueryRowContext(ctx, query, userID, currency).
		Scan(&w.ID, &w.UserID, &w.Currency, &w.Balance, &w.Status, &w.CreatedAt, &w.UpdatedAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrWalletNotFound
		}
		return nil, fmt.Errorf("get wallet: %w", err)
	}
	return w, nil
}

func (r *PostgresWalletRepository) FindOrCreateWallet(ctx context.Context, userID uuid.UUID, currency string) (*domain.Wallet, error) {
	w, err := r.GetWalletByUserAndCurrency(ctx, userID, currency)
	if err == nil {
		return w, nil
	}
	if !errors.Is(err, domain.ErrWalletNotFound) {
		return nil, err
	}

	newID := uuid.New()
	now := time.Now().UTC()
	query := `INSERT INTO wallets (id, user_id, currency, balance, status, created_at, updated_at)
	          VALUES ($1, $2, $3, 0.0000, 'ACTIVE', $4, $5)
	          ON CONFLICT (user_id, currency) DO UPDATE SET updated_at = EXCLUDED.updated_at
	          RETURNING id, user_id, currency, balance, status, created_at, updated_at`

	created := &domain.Wallet{}
	err = r.db.QueryRowContext(ctx, query, newID, userID, currency, now, now).
		Scan(&created.ID, &created.UserID, &created.Currency, &created.Balance, &created.Status, &created.CreatedAt, &created.UpdatedAt)
	if err != nil {
		return nil, fmt.Errorf("create wallet: %w", err)
	}
	return created, nil
}

func (r *PostgresWalletRepository) Deposit(
	ctx context.Context,
	userID uuid.UUID,
	currency string,
	amount float64,
	idempotencyKey, description string,
) (*domain.Transaction, error) {
	if amount <= 0 {
		return nil, domain.ErrInvalidAmount
	}

	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("begin deposit tx: %w", err)
	}
	defer tx.Rollback()

	// Check idempotency
	if idempotencyKey != "" {
		existing := &domain.Transaction{}
		checkQuery := `SELECT id, idempotency_key, source_wallet_id, destination_wallet_id,
		                      source_user_id, destination_user_id, type, amount, currency,
		                      status, description, failure_reason, created_at, completed_at
		               FROM transactions WHERE idempotency_key = $1`
		err := tx.QueryRowContext(ctx, checkQuery, idempotencyKey).
			Scan(&existing.ID, &existing.IdempotencyKey, &existing.SourceWalletID, &existing.DestinationWalletID,
				&existing.SourceUserID, &existing.DestinationUserID, &existing.Type, &existing.Amount,
				&existing.Currency, &existing.Status, &existing.Description, &existing.FailureReason,
				&existing.CreatedAt, &existing.CompletedAt)
		if err == nil {
			return existing, nil
		}
	}

	// Lock or create wallet
	var walletID uuid.UUID
	var currentBalance float64
	var status string
	lockQuery := `SELECT id, balance, status FROM wallets WHERE user_id = $1 AND currency = $2 FOR UPDATE`
	err = tx.QueryRowContext(ctx, lockQuery, userID, currency).Scan(&walletID, &currentBalance, &status)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			walletID = uuid.New()
			insertQuery := `INSERT INTO wallets (id, user_id, currency, balance, status, created_at, updated_at)
			                VALUES ($1, $2, $3, $4, 'ACTIVE', NOW(), NOW())`
			if _, err := tx.ExecContext(ctx, insertQuery, walletID, userID, currency, amount); err != nil {
				return nil, fmt.Errorf("insert wallet for deposit: %w", err)
			}
			currentBalance = 0
		} else {
			return nil, fmt.Errorf("lock wallet: %w", err)
		}
	} else {
		if status != string(domain.WalletStatusActive) {
			return nil, domain.ErrWalletFrozen
		}
		updateQuery := `UPDATE wallets SET balance = balance + $1, updated_at = NOW() WHERE id = $2`
		if _, err := tx.ExecContext(ctx, updateQuery, amount, walletID); err != nil {
			return nil, fmt.Errorf("update wallet balance: %w", err)
		}
	}

	txID := uuid.New()
	now := time.Now().UTC()
	var descPtr *string
	if description != "" {
		descPtr = &description
	}
	var idemPtr *string
	if idempotencyKey != "" {
		idemPtr = &idempotencyKey
	}

	insTxQuery := `INSERT INTO transactions
	               (id, idempotency_key, destination_wallet_id, destination_user_id, type, amount, currency, status, description, created_at, completed_at)
	               VALUES ($1, $2, $3, $4, 'DEPOSIT', $5, $6, 'COMPLETED', $7, $8, $9)`

	_, err = tx.ExecContext(ctx, insTxQuery, txID, idemPtr, walletID, userID, amount, currency, descPtr, now, now)
	if err != nil {
		return nil, fmt.Errorf("record deposit transaction: %w", err)
	}

	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("commit deposit: %w", err)
	}

	return &domain.Transaction{
		ID:                  txID,
		IdempotencyKey:      idemPtr,
		DestinationWalletID: &walletID,
		DestinationUserID:   &userID,
		Type:                domain.TransactionTypeDeposit,
		Amount:              amount,
		Currency:            currency,
		Status:              domain.TransactionStatusCompleted,
		Description:         descPtr,
		CreatedAt:           now,
		CompletedAt:         &now,
	}, nil
}

func (r *PostgresWalletRepository) TransferP2P(
	ctx context.Context,
	sourceUserID, destUserID uuid.UUID,
	currency string,
	amount float64,
	idempotencyKey, description string,
) (*domain.Transaction, error) {
	if sourceUserID == destUserID {
		return nil, domain.ErrSameAccountTransfer
	}
	if amount <= 0 {
		return nil, domain.ErrInvalidAmount
	}

	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("begin transfer tx: %w", err)
	}
	defer tx.Rollback()

	// 1. Check idempotency
	if idempotencyKey != "" {
		existing := &domain.Transaction{}
		checkQuery := `SELECT id, idempotency_key, source_wallet_id, destination_wallet_id,
		                      source_user_id, destination_user_id, type, amount, currency,
		                      status, description, failure_reason, created_at, completed_at
		               FROM transactions WHERE idempotency_key = $1`
		err := tx.QueryRowContext(ctx, checkQuery, idempotencyKey).
			Scan(&existing.ID, &existing.IdempotencyKey, &existing.SourceWalletID, &existing.DestinationWalletID,
				&existing.SourceUserID, &existing.DestinationUserID, &existing.Type, &existing.Amount,
				&existing.Currency, &existing.Status, &existing.Description, &existing.FailureReason,
				&existing.CreatedAt, &existing.CompletedAt)
		if err == nil {
			return existing, nil
		}
	}

	// 2. Lock source wallet with FOR UPDATE
	var srcWalletID uuid.UUID
	var srcBalance float64
	var srcStatus string
	srcLockQuery := `SELECT id, balance, status FROM wallets WHERE user_id = $1 AND currency = $2 FOR UPDATE`
	err = tx.QueryRowContext(ctx, srcLockQuery, sourceUserID, currency).Scan(&srcWalletID, &srcBalance, &srcStatus)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, fmt.Errorf("source wallet not found for currency %s", currency)
		}
		return nil, fmt.Errorf("lock source wallet: %w", err)
	}

	if srcStatus != string(domain.WalletStatusActive) {
		return nil, domain.ErrWalletFrozen
	}
	if srcBalance < amount {
		return nil, domain.ErrInsufficientFunds
	}

	// 3. Lock or create destination wallet
	var dstWalletID uuid.UUID
	var dstStatus string
	dstLockQuery := `SELECT id, status FROM wallets WHERE user_id = $1 AND currency = $2 FOR UPDATE`
	err = tx.QueryRowContext(ctx, dstLockQuery, destUserID, currency).Scan(&dstWalletID, &dstStatus)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			dstWalletID = uuid.New()
			insWalletQuery := `INSERT INTO wallets (id, user_id, currency, balance, status, created_at, updated_at)
			                  VALUES ($1, $2, $3, 0.0000, 'ACTIVE', NOW(), NOW())`
			if _, err := tx.ExecContext(ctx, insWalletQuery, dstWalletID, destUserID, currency); err != nil {
				return nil, fmt.Errorf("create dest wallet: %w", err)
			}
		} else {
			return nil, fmt.Errorf("lock dest wallet: %w", err)
		}
	} else if dstStatus != string(domain.WalletStatusActive) {
		return nil, fmt.Errorf("destination wallet is frozen or closed")
	}

	// 4. Atomic debit and credit
	debitQuery := `UPDATE wallets SET balance = balance - $1, updated_at = NOW() WHERE id = $2`
	if _, err := tx.ExecContext(ctx, debitQuery, amount, srcWalletID); err != nil {
		return nil, fmt.Errorf("debit source wallet: %w", err)
	}

	creditQuery := `UPDATE wallets SET balance = balance + $1, updated_at = NOW() WHERE id = $2`
	if _, err := tx.ExecContext(ctx, creditQuery, amount, dstWalletID); err != nil {
		return nil, fmt.Errorf("credit dest wallet: %w", err)
	}

	// 5. Record transaction record
	txID := uuid.New()
	now := time.Now().UTC()
	var descPtr *string
	if description != "" {
		descPtr = &description
	}
	var idemPtr *string
	if idempotencyKey != "" {
		idemPtr = &idempotencyKey
	}

	insTxQuery := `INSERT INTO transactions
	               (id, idempotency_key, source_wallet_id, destination_wallet_id, source_user_id, destination_user_id,
	                type, amount, currency, status, description, created_at, completed_at)
	               VALUES ($1, $2, $3, $4, $5, $6, 'TRANSFER_P2P', $7, $8, 'COMPLETED', $9, $10, $11)`

	_, err = tx.ExecContext(ctx, insTxQuery, txID, idemPtr, srcWalletID, dstWalletID, sourceUserID, destUserID, amount, currency, descPtr, now, now)
	if err != nil {
		return nil, fmt.Errorf("record transfer transaction: %w", err)
	}

	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("commit transfer: %w", err)
	}

	return &domain.Transaction{
		ID:                  txID,
		IdempotencyKey:      idemPtr,
		SourceWalletID:      &srcWalletID,
		DestinationWalletID: &dstWalletID,
		SourceUserID:        &sourceUserID,
		DestinationUserID:   &destUserID,
		Type:                domain.TransactionTypeTransfer,
		Amount:              amount,
		Currency:            currency,
		Status:              domain.TransactionStatusCompleted,
		Description:         descPtr,
		CreatedAt:           now,
		CompletedAt:         &now,
	}, nil
}

func (r *PostgresWalletRepository) GetDailySpentAmount(ctx context.Context, userID uuid.UUID) (float64, error) {
	query := `SELECT COALESCE(SUM(amount), 0.0)
	          FROM transactions
	          WHERE source_user_id = $1
	            AND type = 'TRANSFER_P2P'
	            AND status = 'COMPLETED'
	            AND created_at >= CURRENT_DATE`

	var spent float64
	err := r.db.QueryRowContext(ctx, query, userID).Scan(&spent)
	if err != nil {
		return 0.0, fmt.Errorf("get daily spent: %w", err)
	}
	return spent, nil
}

func (r *PostgresWalletRepository) GetTransactionsByUserID(ctx context.Context, userID uuid.UUID, limit, offset int) ([]*domain.Transaction, int, error) {
	countQuery := `SELECT COUNT(*) FROM transactions WHERE source_user_id = $1 OR destination_user_id = $1`
	var total int
	if err := r.db.QueryRowContext(ctx, countQuery, userID).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("count transactions: %w", err)
	}

	query := `SELECT id, idempotency_key, source_wallet_id, destination_wallet_id,
	                 source_user_id, destination_user_id, type, amount, currency,
	                 status, description, failure_reason, created_at, completed_at
	          FROM transactions
	          WHERE source_user_id = $1 OR destination_user_id = $1
	          ORDER BY created_at DESC
	          LIMIT $2 OFFSET $3`

	rows, err := r.db.QueryContext(ctx, query, userID, limit, offset)
	if err != nil {
		return nil, 0, fmt.Errorf("query transactions: %w", err)
	}
	defer rows.Close()

	var transactions []*domain.Transaction
	for rows.Next() {
		t := &domain.Transaction{}
		err := rows.Scan(&t.ID, &t.IdempotencyKey, &t.SourceWalletID, &t.DestinationWalletID,
			&t.SourceUserID, &t.DestinationUserID, &t.Type, &t.Amount, &t.Currency,
			&t.Status, &t.Description, &t.FailureReason, &t.CreatedAt, &t.CompletedAt)
		if err != nil {
			return nil, 0, fmt.Errorf("scan transaction: %w", err)
		}
		transactions = append(transactions, t)
	}

	return transactions, total, nil
}
