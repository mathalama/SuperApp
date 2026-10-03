package app

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"dev.mathalama/userservice/internal/config"
	"dev.mathalama/userservice/internal/consumer"
	"dev.mathalama/userservice/internal/handler"
	"dev.mathalama/userservice/internal/migrations"
	"dev.mathalama/userservice/internal/repository"
	"dev.mathalama/userservice/internal/service"
	"dev.mathalama/userservice/internal/storage"
	"github.com/go-chi/chi/v5"
	chimiddleware "github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	_ "github.com/lib/pq"
)

type App struct {
	cfg        *config.Config
	server     *http.Server
	db         *sql.DB
	consumer   *consumer.UserEventConsumer
}

func NewApp(cfg *config.Config) (*App, error) {
	// 1. Connect to PostgreSQL
	log.Printf("[App] Connecting to PostgreSQL...")
	db, err := sql.Open("postgres", cfg.PostgresURL)
	if err != nil {
		return nil, fmt.Errorf("failed opening postgres db: %w", err)
	}

	db.SetMaxOpenConns(25)
	db.SetMaxIdleConns(10)
	db.SetConnMaxLifetime(5 * time.Minute)

	// Ping database with timeout
	ctxPing, cancelPing := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancelPing()
	if err := db.PingContext(ctxPing); err != nil {
		log.Printf("[App WARN] Postgres ping failed (will retry on query): %v", err)
	} else {
		log.Printf("[App] Postgres connected successfully.")
		// Run migrations
		if err := migrations.Run(ctxPing, db); err != nil {
			log.Printf("[App WARN] Migrations warning: %v", err)
		}
	}

	// 2. Initialize MinIO Avatar Storage
	avatarStorage, err := storage.NewMinioAvatarStorage(
		cfg.S3Endpoint,
		cfg.S3AccessKey,
		cfg.S3SecretKey,
		cfg.S3BucketAvatars,
		cfg.S3PublicURL,
	)
	if err != nil {
		log.Printf("[App WARN] Failed initializing MinIO storage: %v", err)
	}

	// 3. Setup Architecture Layers
	repo := repository.NewPostgresUserProfileRepository(db)
	svc := service.NewUserProfileService(repo, avatarStorage)
	h := handler.NewUserProfileHandler(svc)

	// 4. Setup Chi Router
	r := chi.NewRouter()
	r.Use(chimiddleware.RequestID)
	r.Use(chimiddleware.RealIP)
	r.Use(chimiddleware.Logger)
	r.Use(chimiddleware.Recoverer)

	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   []string{"*"},
		AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "X-CSRF-Token", "X-User-Id", "X-User-Roles"},
		ExposedHeaders:   []string{"Link"},
		AllowCredentials: true,
		MaxAge:           300,
	}))

	h.RegisterRoutes(r)

	walletRepo := repository.NewPostgresWalletRepository(db)
	walletSvc := service.NewWalletService(walletRepo, repo)
	walletHandler := handler.NewWalletHandler(walletSvc)
	walletHandler.RegisterRoutes(r)

	// 5. Setup Kafka Consumer
	userConsumer := consumer.NewUserEventConsumer(cfg, svc)

	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      r,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	return &App{
		cfg:      cfg,
		server:   srv,
		db:       db,
		consumer: userConsumer,
	}, nil
}

func (a *App) Run() error {
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM, syscall.SIGINT)
	defer cancel()

	// Start Kafka consumer
	a.consumer.Start(ctx)

	// Start HTTP server in background
	serverErr := make(chan error, 1)
	go func() {
		log.Printf("[App] User Service starting on port %s", a.cfg.Port)
		if err := a.server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serverErr <- err
		}
	}()

	select {
	case err := <-serverErr:
		return fmt.Errorf("server error: %w", err)
	case <-ctx.Done():
		log.Println("[App] Graceful shutdown initiated...")
	}

	// Graceful shutdown with 15s timeout
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer shutdownCancel()

	if err := a.server.Shutdown(shutdownCtx); err != nil {
		log.Printf("[App ERROR] HTTP server shutdown error: %v", err)
	}

	if err := a.consumer.Close(); err != nil {
		log.Printf("[App ERROR] Kafka consumer close error: %v", err)
	}

	if err := a.db.Close(); err != nil {
		log.Printf("[App ERROR] Database close error: %v", err)
	}

	log.Println("[App] User Service successfully stopped.")
	return nil
}
