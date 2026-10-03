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

	"dev.mathalama/apigateway/internal/config"
	"dev.mathalama/apigateway/internal/middleware"
	"dev.mathalama/apigateway/internal/proxy"

	"github.com/go-chi/chi/v5"
	chimiddleware "github.com/go-chi/chi/v5/middleware"
	"github.com/redis/go-redis/v9"
)

type App struct {
	cfg    *config.Config
	server *http.Server
	rdb    *redis.Client
}

func New(cfg *config.Config) (*App, error) {
	// 1. Redis
	redisAddr := fmt.Sprintf("%s:%s", cfg.RedisHost, cfg.RedisPort)
	rdb := redis.NewClient(&redis.Options{
		Addr:     redisAddr,
		Password: cfg.RedisPassword,
		DB:       0,
	})

	// Async Redis health check (does not block server startup)
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		if err := rdb.Ping(ctx).Err(); err != nil {
			log.Printf("[Gateway WARN] Redis connection at %s failed: %v", redisAddr, err)
		} else {
			log.Printf("[Gateway] Connected to Redis at %s", redisAddr)
		}
	}()

	// 2. Initialize Reverse Proxies
	identityProxy, err := proxy.NewReverseProxy(cfg.IdentityServiceURL, "")
	if err != nil {
		return nil, fmt.Errorf("identity proxy: %w", err)
	}
	identityDocsProxy, err := proxy.NewReverseProxy(cfg.IdentityServiceURL, "/identity")
	if err != nil {
		return nil, fmt.Errorf("identity docs proxy: %w", err)
	}
	userProxy, err := proxy.NewReverseProxy(cfg.UserServiceURL, "")
	if err != nil {
		return nil, fmt.Errorf("user proxy: %w", err)
	}
	userDocsProxy, err := proxy.NewReverseProxy(cfg.UserServiceURL, "/user")
	if err != nil {
		return nil, fmt.Errorf("user docs proxy: %w", err)
	}
	kycProxy, err := proxy.NewReverseProxy(cfg.KycServiceURL, "")
	if err != nil {
		return nil, fmt.Errorf("kyc proxy: %w", err)
	}
	kycDocsProxy, err := proxy.NewReverseProxy(cfg.KycServiceURL, "/kyc")
	if err != nil {
		return nil, fmt.Errorf("kyc docs proxy: %w", err)
	}
	kycMlProxy, err := proxy.NewReverseProxy(cfg.KycMlServiceURL, "")
	if err != nil {
		return nil, fmt.Errorf("kyc ml proxy: %w", err)
	}
	notificationProxy, err := proxy.NewReverseProxy(cfg.NotificationServiceURL, "")
	if err != nil {
		return nil, fmt.Errorf("notification proxy: %w", err)
	}

	// 3. Chi Router & Middlewares
	r := chi.NewRouter()
	r.Use(chimiddleware.RequestID)
	r.Use(chimiddleware.RealIP)
	r.Use(chimiddleware.Logger)
	r.Use(chimiddleware.Recoverer)
	r.Use(middleware.NewCorsMiddleware())

	// JWT Relay
	jwtRelay := middleware.NewJwtRelayMiddleware(cfg.JwtSecret, rdb)
	r.Use(jwtRelay.Handler)

	// Health Check (Spring Boot Actuator compatible)
	healthHandler := func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"status":    "UP",
			"timestamp": time.Now().UTC().Format(time.RFC3339),
			"gateway":   "Go/Chi",
		})
	}
	r.Get("/actuator/health", healthHandler)
	r.Get("/health", healthHandler)

	// Downstream Route Mappings
	r.Handle("/auth/*", identityProxy)
	r.Handle("/auth", identityProxy)
	r.Handle("/oauth2/*", identityProxy)
	r.Handle("/login/oauth2/*", identityProxy)
	r.Handle("/.well-known/*", identityProxy)
	r.Handle("/identity/v3/api-docs/*", identityDocsProxy)
	r.Handle("/identity/v3/api-docs", identityDocsProxy)

	r.Handle("/api/users/*", userProxy)
	r.Handle("/api/users", userProxy)
	r.Handle("/api/wallets/*", userProxy)
	r.Handle("/api/wallets", userProxy)
	r.Handle("/user/v3/api-docs/*", userDocsProxy)
	r.Handle("/user/v3/api-docs", userDocsProxy)

	r.Handle("/api/kyc/*", kycProxy)
	r.Handle("/api/kyc", kycProxy)
	r.Handle("/kyc/v3/api-docs/*", kycDocsProxy)
	r.Handle("/kyc/v3/api-docs", kycDocsProxy)

	r.Handle("/api/v1/*", kycMlProxy)
	r.Handle("/api/v1", kycMlProxy)

	r.Handle("/api/notifications/*", notificationProxy)
	r.Handle("/api/notifications", notificationProxy)

	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      r,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 60 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	return &App{
		cfg:    cfg,
		server: srv,
		rdb:    rdb,
	}, nil
}

func (a *App) Run() error {
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)

	go func() {
		log.Printf("[Gateway READY] Listening on http://0.0.0.0:%s", a.cfg.Port)
		if err := a.server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("[Gateway ERROR] HTTP server failed: %v", err)
		}
	}()

	<-stop
	log.Println("[Gateway] Shutting down gracefully...")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := a.server.Shutdown(shutdownCtx); err != nil {
		log.Printf("[Gateway ERROR] Forced shutdown error: %v", err)
	}

	_ = a.rdb.Close()
	log.Println("[Gateway] Server exited cleanly.")
	return nil
}
