package middleware

import (
	"net/http"

	"github.com/go-chi/cors"
)

// NewCorsMiddleware creates a CORS handler matching the Spring Cloud Gateway CorsConfig.
func NewCorsMiddleware() func(http.Handler) http.Handler {
	return cors.Handler(cors.Options{
		AllowedOrigins:   []string{"*"},
		AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"*"},
		AllowCredentials: true,
		MaxAge:           3600,
	})
}
