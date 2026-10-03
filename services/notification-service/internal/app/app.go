package app

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"dev.mathalama/notificationservice/internal/config"
	"dev.mathalama/notificationservice/internal/consumer"
	"dev.mathalama/notificationservice/internal/service"

	"github.com/go-chi/chi/v5"
	chimiddleware "github.com/go-chi/chi/v5/middleware"
	"github.com/redis/go-redis/v9"
)

type App struct {
	cfg      *config.Config
	server   *http.Server
	rdb      *redis.Client
	consumer *consumer.NotificationConsumer
}

func New(cfg *config.Config) (*App, error) {
	// 1. Initialize Redis client
	redisAddr := fmt.Sprintf("%s:%s", cfg.RedisHost, cfg.RedisPort)
	rdb := redis.NewClient(&redis.Options{
		Addr:     redisAddr,
		Password: cfg.RedisPassword,
		DB:       0,
	})

	// Async Redis test
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		if err := rdb.Ping(ctx).Err(); err != nil {
			log.Printf("[Notification WARN] Redis connection at %s failed: %v", redisAddr, err)
		} else {
			log.Printf("[Notification] Connected to Redis at %s", redisAddr)
		}
	}()

	// 2. Initialize Services
	idemSvc := service.NewIdempotencyService(rdb)
	emailSvc := service.NewEmailService(cfg)
	sseHub := service.NewSSEHub()
	notifConsumer := consumer.NewNotificationConsumer(cfg, emailSvc, idemSvc, sseHub)

	// 3. Initialize Chi Router
	r := chi.NewRouter()
	r.Use(chimiddleware.RequestID)
	r.Use(chimiddleware.RealIP)
	r.Use(chimiddleware.Logger)
	r.Use(chimiddleware.Recoverer)

	healthHandler := func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"status":    "UP",
			"timestamp": time.Now().UTC().Format(time.RFC3339),
			"service":   "notification-service (Go)",
		})
	}
	r.Get("/actuator/health", healthHandler)
	r.Get("/health", healthHandler)
	r.Get("/api/notifications/health", healthHandler)
	r.Get("/api/notifications/stream", sseHub.ServeHTTP)

	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      r,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 0, // 0 enables indefinite SSE streaming without timeout cutoff
		IdleTimeout:  60 * time.Second,
	}

	return &App{
		cfg:      cfg,
		server:   srv,
		rdb:      rdb,
		consumer: notifConsumer,
	}, nil
}

func (a *App) Run() error {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// 1. Start Kafka Consumer in Background
	a.consumer.Start(ctx)

	// 2. Start HTTP Health Server
	go func() {
		log.Printf("[Notification READY] Health server listening on http://0.0.0.0:%s", a.cfg.Port)
		if err := a.server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("[Notification ERROR] HTTP server failed: %v", err)
		}
	}()

	// 3. Wait for Termination Signal
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)

	<-stop
	log.Println("[Notification] Shutting down gracefully...")

	// Cancel Kafka consumer context
	cancel()
	_ = a.consumer.Close()

	// Shutdown HTTP server
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer shutdownCancel()
	_ = a.server.Shutdown(shutdownCtx)

	_ = a.rdb.Close()
	log.Println("[Notification] Server exited cleanly.")
	return nil
}
