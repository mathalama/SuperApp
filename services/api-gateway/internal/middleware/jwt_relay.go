package middleware

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/redis/go-redis/v9"
)

var publicPaths = map[string]struct{}{
	"/auth/register":                 {},
	"/auth/authenticate":             {},
	"/auth/refresh":                  {},
	"/auth/verify-email":             {},
	"/auth/resend-verification":      {},
	"/auth/forgot-password":          {},
	"/auth/reset-password":           {},
	"/auth/reset-forgotten-password": {},
	"/auth/oauth-exchange":           {},
	"/api/v1/head-pose-check":        {},
	"/api/v1/liveness/challenge":     {},
	"/api/v1/liveness/evaluate-frame": {},
	"/actuator/health":               {},
	"/health":                        {},
}

var publicPrefixes = []string{
	"/identity/v3/api-docs",
	"/user/v3/api-docs",
	"/kyc/v3/api-docs",
	"/swagger-ui",
	"/login/oauth2",
	"/oauth2",
	"/.well-known",
}

type CustomClaims struct {
	Type  string      `json:"type"`
	Roles interface{} `json:"roles"`
	jwt.RegisteredClaims
}

type JwtRelayMiddleware struct {
	jwtSecret   []byte
	redisClient *redis.Client
}

func NewJwtRelayMiddleware(jwtSecret string, redisClient *redis.Client) *JwtRelayMiddleware {
	return &JwtRelayMiddleware{
		jwtSecret:   []byte(jwtSecret),
		redisClient: redisClient,
	}
}

func (m *JwtRelayMiddleware) Handler(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		path := r.URL.Path

		// 1. Check if route is public
		if isPublicPath(path) {
			// Strip any user spoofing headers from untrusted clients
			r.Header.Del("X-User-Id")
			r.Header.Del("X-User-Roles")
			next.ServeHTTP(w, r)
			return
		}

		// 2. Validate Authorization header presence
		authHeader := r.Header.Get("Authorization")
		if authHeader == "" || !strings.HasPrefix(authHeader, "Bearer ") {
			http.Error(w, "Unauthorized: Missing or invalid Authorization header", http.StatusUnauthorized)
			return
		}

		tokenStr := strings.TrimPrefix(authHeader, "Bearer ")

		// 3. Parse and verify HMAC signature
		var claims CustomClaims
		token, err := jwt.ParseWithClaims(tokenStr, &claims, func(t *jwt.Token) (interface{}, error) {
			if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
				return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
			}
			return m.jwtSecret, nil
		})

		if err != nil || !token.Valid {
			log.Printf("[Gateway] JWT validation failed: %v", err)
			http.Error(w, "Unauthorized: Invalid token", http.StatusUnauthorized)
			return
		}

		// 4. Verify token type is "access"
		if claims.Type != "access" {
			log.Printf("[Gateway] Invalid token type: %s", claims.Type)
			http.Error(w, "Unauthorized: Token is not an access token", http.StatusUnauthorized)
			return
		}

		// 5. Check Redis blacklist for revoked access tokens (post-logout)
		if m.redisClient != nil && claims.ID != "" {
			ctx, cancel := context.WithTimeout(r.Context(), 1*time.Second)
			defer cancel()

			key := "blacklist:access:" + claims.ID
			exists, rErr := m.redisClient.Exists(ctx, key).Result()
			if rErr == nil && exists > 0 {
				log.Printf("[Gateway] Access token is blacklisted, jti: %s", claims.ID)
				http.Error(w, "Unauthorized: Token has been revoked", http.StatusUnauthorized)
				return
			}
		}

		// 6. Extract user identity and roles
		userId := claims.Subject
		rolesHeader := extractRoles(claims.Roles)

		// 7. Inject trusted headers downstream
		r.Header.Set("X-User-Id", userId)
		r.Header.Set("X-User-Roles", rolesHeader)

		next.ServeHTTP(w, r)
	})
}

func isPublicPath(path string) bool {
	if _, ok := publicPaths[path]; ok {
		return true
	}
	for _, prefix := range publicPrefixes {
		if strings.HasPrefix(path, prefix) {
			return true
		}
	}
	return false
}

func extractRoles(raw interface{}) string {
	if raw == nil {
		return ""
	}
	switch v := raw.(type) {
	case string:
		return v
	case []string:
		return strings.Join(v, ",")
	case []interface{}:
		var parts []string
		for _, item := range v {
			if s, ok := item.(string); ok {
				parts = append(parts, s)
			}
		}
		return strings.Join(parts, ",")
	default:
		return ""
	}
}
