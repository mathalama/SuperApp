package main

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

func main() {
	cfg := config.Load()
	log.Printf("[Gateway] Starting SuperApp API Gateway on port :%s...", cfg.Port)

	// 1. Initialize Redis client
	redisAddr := fmt.Sprintf("%s:%s", cfg.RedisHost, cfg.RedisPort)
	rdb := redis.NewClient(&redis.Options{
		Addr:     redisAddr,
		Password: cfg.RedisPassword,
		DB:       0,
	})

	// Test Redis connection asynchronously with quick timeout
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		if err := rdb.Ping(ctx).Err(); err != nil {
			log.Printf("[Gateway WARN] Redis connection at %s failed: %v (token blacklist checks will be bypassed until Redis is available)", redisAddr, err)
		} else {
			log.Printf("[Gateway] Successfully connected to Redis at %s", redisAddr)
		}
	}()

	// 2. Initialize Reverse Proxies
	identityProxy, err := proxy.NewReverseProxy(cfg.IdentityServiceURL, "")
	if err != nil {
		log.Fatalf("Failed to init identity proxy: %v", err)
	}

	identityDocsProxy, err := proxy.NewReverseProxy(cfg.IdentityServiceURL, "/identity")
	if err != nil {
		log.Fatalf("Failed to init identity docs proxy: %v", err)
	}

	userProxy, err := proxy.NewReverseProxy(cfg.UserServiceURL, "")
	if err != nil {
		log.Fatalf("Failed to init user proxy: %v", err)
	}

	userDocsProxy, err := proxy.NewReverseProxy(cfg.UserServiceURL, "/user")
	if err != nil {
		log.Fatalf("Failed to init user docs proxy: %v", err)
	}

	kycProxy, err := proxy.NewReverseProxy(cfg.KycServiceURL, "")
	if err != nil {
		log.Fatalf("Failed to init kyc proxy: %v", err)
	}

	kycDocsProxy, err := proxy.NewReverseProxy(cfg.KycServiceURL, "/kyc")
	if err != nil {
		log.Fatalf("Failed to init kyc docs proxy: %v", err)
	}

	kycMlProxy, err := proxy.NewReverseProxy(cfg.KycMlServiceURL, "")
	if err != nil {
		log.Fatalf("Failed to init kyc ml proxy: %v", err)
	}

	notificationProxy, err := proxy.NewReverseProxy(cfg.NotificationServiceURL, "")
	if err != nil {
		log.Fatalf("Failed to init notification proxy: %v", err)
	}

	// 3. Setup Chi Router & Middlewares
	r := chi.NewRouter()

	r.Use(chimiddleware.RequestID)
	r.Use(chimiddleware.RealIP)
	r.Use(chimiddleware.Logger)
	r.Use(chimiddleware.Recoverer)
	r.Use(middleware.NewCorsMiddleware())

	// Health Check Handlers (Spring Boot Actuator compatibility)
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

	// 4. Mount JWT Relay Filter
	jwtRelay := middleware.NewJwtRelayMiddleware(cfg.JwtSecret, rdb)
	r.Use(jwtRelay.Handler)

	// 5. Mount Routes Matching Spring Cloud Gateway Routes

	// Identity Service
	r.Handle("/auth/*", identityProxy)
	r.Handle("/auth", identityProxy)
	r.Handle("/oauth2/*", identityProxy)
	r.Handle("/login/oauth2/*", identityProxy)
	r.Handle("/.well-known/*", identityProxy)
	r.Handle("/identity/v3/api-docs/*", identityDocsProxy)
	r.Handle("/identity/v3/api-docs", identityDocsProxy)

	// User Service
	r.Handle("/api/users/*", userProxy)
	r.Handle("/api/users", userProxy)
	r.Handle("/user/v3/api-docs/*", userDocsProxy)
	r.Handle("/user/v3/api-docs", userDocsProxy)

	// KYC Service
	r.Handle("/api/kyc/*", kycProxy)
	r.Handle("/api/kyc", kycProxy)
	r.Handle("/kyc/v3/api-docs/*", kycDocsProxy)
	r.Handle("/kyc/v3/api-docs", kycDocsProxy)

	// KYC ML Service (Head Pose & Fast Liveness Checks)
	r.Handle("/api/v1/*", kycMlProxy)
	r.Handle("/api/v1", kycMlProxy)

	// Notification Service
	r.Handle("/api/notifications/*", notificationProxy)
	r.Handle("/api/notifications", notificationProxy)

	// 6. Start HTTP Server with Graceful Shutdown
	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      r,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 60 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)

	go func() {
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("[Gateway ERROR] HTTP server failed: %v", err)
		}
	}()

	log.Printf("[Gateway READY] Listening on http://0.0.0.0:%s", cfg.Port)

	<-stop
	log.Println("[Gateway] Shutting down gracefully...")

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer shutdownCancel()

	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Printf("[Gateway ERROR] Forced shutdown error: %v", err)
	}

	_ = rdb.Close()
	log.Println("[Gateway] Server exited cleanly.")
}
