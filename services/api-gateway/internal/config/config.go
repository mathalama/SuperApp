package config

import (
	"os"
)

type Config struct {
	Port                   string
	JwtSecret              string
	FrontendURL            string
	RedisHost              string
	RedisPort              string
	RedisPassword          string
	IdentityServiceURL     string
	UserServiceURL         string
	NotificationServiceURL string
	KycServiceURL          string
	KycMlServiceURL        string
}

func Load() *Config {
	return &Config{
		Port:                   getEnvAny([]string{"PORT", "SERVER_PORT", "GATEWAY_PORT"}, "8010"),
		JwtSecret:              getEnv("JWT_SECRET", "super-secret-key-for-development-must-be-changed-in-production"),
		FrontendURL:            getEnv("FRONTEND_URL", "http://localhost:3000"),
		RedisHost:              getEnvAny([]string{"SPRING_DATA_REDIS_HOST", "REDIS_HOST"}, "redis"),
		RedisPort:              getEnvAny([]string{"SPRING_DATA_REDIS_PORT", "REDIS_PORT"}, "6379"),
		RedisPassword:          getEnv("REDIS_PASSWORD", ""),
		IdentityServiceURL:     getEnv("IDENTITY_SERVICE_URL", "http://identity-service:8080"),
		UserServiceURL:         getEnv("USER_SERVICE_URL", "http://user-service:8083"),
		NotificationServiceURL: getEnv("NOTIFICATION_SERVICE_URL", "http://notification-service:8084"),
		KycServiceURL:          getEnv("KYC_SERVICE_URL", "http://kyc-service:8085"),
		KycMlServiceURL:        getEnv("KYC_ML_SERVICE_URL", "http://kyc-ml-service:8000"),
	}
}

func getEnv(key, defaultVal string) string {
	if val, ok := os.LookupEnv(key); ok && val != "" {
		return val
	}
	return defaultVal
}

func getEnvAny(keys []string, defaultVal string) string {
	for _, key := range keys {
		if val, ok := os.LookupEnv(key); ok && val != "" {
			return val
		}
	}
	return defaultVal
}
