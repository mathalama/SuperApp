package config

import (
	"fmt"
	"net/url"
	"os"
	"strings"
)

type Config struct {
	Port                     string
	PostgresURL              string
	S3Endpoint               string
	S3AccessKey              string
	S3SecretKey              string
	S3BucketAvatars          string
	S3PublicURL              string
	KafkaBootstrapServers    string
	KafkaGroupID             string
	KafkaTopicUserRegistered string
}

func Load() *Config {
	port := getEnvAny([]string{"PORT", "SERVER_PORT"}, "8083")

	// S3 / MinIO settings
	s3Endpoint := getEnvAny([]string{"S3_ENDPOINT"}, "http://localhost:8504")
	s3AccessKey := getEnvAny([]string{"S3_ACCESS_KEY", "S3_USER"}, "minioadmin")
	s3SecretKey := getEnvAny([]string{"S3_SECRET_KEY", "S3_PASSWORD"}, "minioadmin")
	s3BucketAvatars := getEnvAny([]string{"S3_BUCKET_AVATARS"}, "avatars")
	s3PublicURL := getEnvAny([]string{"S3_PUBLIC_URL"}, "http://localhost:8504")

	// Kafka settings
	kafkaServers := getEnvAny([]string{"KAFKA_BOOTSTRAP_SERVERS", "SPRING_KAFKA_BOOTSTRAP_SERVERS"}, "localhost:9092")
	kafkaGroup := getEnvAny([]string{"KAFKA_GROUP_ID", "APP_KAFKA_CONSUMER_USER_GROUP"}, "user-service-group")
	kafkaTopic := getEnvAny([]string{"KAFKA_TOPIC_USER_REGISTERED", "APP_KAFKA_TOPICS_USER_REGISTERED"}, "user-registered-topic")

	// Database Connection String
	pgURL := buildPostgresURL()

	return &Config{
		Port:                     port,
		PostgresURL:              pgURL,
		S3Endpoint:               s3Endpoint,
		S3AccessKey:              s3AccessKey,
		S3SecretKey:              s3SecretKey,
		S3BucketAvatars:          s3BucketAvatars,
		S3PublicURL:              strings.TrimRight(s3PublicURL, "/"),
		KafkaBootstrapServers:    kafkaServers,
		KafkaGroupID:             kafkaGroup,
		KafkaTopicUserRegistered: kafkaTopic,
	}
}

func buildPostgresURL() string {
	rawURL := getEnvAny([]string{"DATABASE_URL", "POSTGRES_URL", "SPRING_DATASOURCE_URL"}, "")
	if rawURL != "" {
		// Convert jdbc:postgresql://... to standard postgres://...
		if strings.HasPrefix(rawURL, "jdbc:postgresql://") {
			rawURL = strings.TrimPrefix(rawURL, "jdbc:")
		}
		// If credentials are in separate vars and not in rawURL, parse and add
		user := getEnvAny([]string{"POSTGRES_USER", "SPRING_DATASOURCE_USERNAME"}, "postgres")
		pass := getEnvAny([]string{"POSTGRES_PASSWORD", "SPRING_DATASOURCE_PASSWORD"}, "postgres")

		u, err := url.Parse(rawURL)
		if err == nil {
			if u.User == nil || u.User.Username() == "" {
				u.User = url.UserPassword(user, pass)
			}
			q := u.Query()
			if q.Get("sslmode") == "" {
				q.Set("sslmode", "disable")
			}
			u.RawQuery = q.Encode()
			return u.String()
		}
	}

	user := getEnvAny([]string{"POSTGRES_USER", "SPRING_DATASOURCE_USERNAME"}, "postgres")
	pass := getEnvAny([]string{"POSTGRES_PASSWORD", "SPRING_DATASOURCE_PASSWORD"}, "postgres")
	host := getEnvAny([]string{"POSTGRES_HOST"}, "localhost")
	port := getEnvAny([]string{"POSTGRES_PORT"}, "5432")
	dbName := getEnvAny([]string{"USER_DB", "POSTGRES_DB"}, "user_db")

	return fmt.Sprintf("postgres://%s:%s@%s:%s/%s?sslmode=disable",
		url.QueryEscape(user),
		url.QueryEscape(pass),
		host,
		port,
		dbName,
	)
}

func getEnvAny(keys []string, defaultVal string) string {
	for _, k := range keys {
		if val, ok := os.LookupEnv(k); ok && val != "" {
			return val
		}
	}
	return defaultVal
}
