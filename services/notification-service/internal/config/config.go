package config

import (
	"os"
)

type Config struct {
	Port                       string
	KafkaBootstrapServers      string
	KafkaGroupID               string
	KafkaTopicUserRegistered   string
	KafkaTopicVerificationEmail string
	KafkaTopicPasswordReset    string
	RedisHost                  string
	RedisPort                  string
	RedisPassword              string
	MailHost                   string
	MailPort                   string
	MailUsername               string
	MailPassword               string
	MailFromName               string
	FrontendURL                string
}

func Load() *Config {
	return &Config{
		Port:                       getEnvAny([]string{"PORT", "SERVER_PORT"}, "8084"),
		KafkaBootstrapServers:      getEnvAny([]string{"SPRING_KAFKA_BOOTSTRAP_SERVERS", "KAFKA_BOOTSTRAP_SERVERS"}, "kafka:29092"),
		KafkaGroupID:               getEnvAny([]string{"KAFKA_GROUP_ID", "NOTIFICATION_GROUP"}, "notification-group"),
		KafkaTopicUserRegistered:   getEnv("TOPIC_USER_REGISTERED", "user-registered-topic"),
		KafkaTopicVerificationEmail: getEnv("TOPIC_VERIFICATION_EMAIL", "verification-email-topic"),
		KafkaTopicPasswordReset:    getEnv("TOPIC_PASSWORD_RESET", "password-reset-email-topic"),
		RedisHost:                  getEnvAny([]string{"SPRING_DATA_REDIS_HOST", "REDIS_HOST"}, "redis"),
		RedisPort:                  getEnvAny([]string{"SPRING_DATA_REDIS_PORT", "REDIS_PORT"}, "6379"),
		RedisPassword:              getEnv("REDIS_PASSWORD", ""),
		MailHost:                   getEnvAny([]string{"SPRING_MAIL_HOST", "MAIL_HOST"}, "localhost"),
		MailPort:                   getEnvAny([]string{"SPRING_MAIL_PORT", "MAIL_PORT"}, "587"),
		MailUsername:               getEnvAny([]string{"SPRING_MAIL_USERNAME", "MAIL_USERNAME"}, "noreply@example.com"),
		MailPassword:               getEnvAny([]string{"SPRING_MAIL_PASSWORD", "MAIL_PASSWORD"}, ""),
		MailFromName:               getEnv("MAIL_FROM_NAME", "SuperApp"),
		FrontendURL:                getEnv("FRONTEND_URL", "http://localhost:3000"),
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
