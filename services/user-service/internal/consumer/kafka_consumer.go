package consumer

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"strings"
	"time"

	"dev.mathalama/userservice/internal/config"
	"dev.mathalama/userservice/internal/dto"
	"dev.mathalama/userservice/internal/service"
	"github.com/google/uuid"
	"github.com/segmentio/kafka-go"
)

type UserEventConsumer struct {
	cfg    *config.Config
	svc    *service.UserProfileService
	reader *kafka.Reader
}

func NewUserEventConsumer(cfg *config.Config, svc *service.UserProfileService) *UserEventConsumer {
	brokers := strings.Split(cfg.KafkaBootstrapServers, ",")

	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:        brokers,
		GroupID:        cfg.KafkaGroupID,
		Topic:          cfg.KafkaTopicUserRegistered,
		MinBytes:       10e3, // 10KB
		MaxBytes:       10e6, // 10MB
		CommitInterval: 1 * time.Second,
		StartOffset:    kafka.FirstOffset,
	})

	return &UserEventConsumer{
		cfg:    cfg,
		svc:    svc,
		reader: reader,
	}
}

func (c *UserEventConsumer) Start(ctx context.Context) {
	log.Printf("[UserEventConsumer] Starting consumer on topic '%s' (group: '%s')",
		c.cfg.KafkaTopicUserRegistered, c.cfg.KafkaGroupID)

	go func() {
		for {
			m, err := c.reader.FetchMessage(ctx)
			if err != nil {
				if errors.Is(err, context.Canceled) {
					return
				}
				log.Printf("[UserEventConsumer Error] Fetch %s: %v", c.cfg.KafkaTopicUserRegistered, err)
				time.Sleep(1 * time.Second)
				continue
			}

			var event dto.UserRegisteredEvent
			if err := json.Unmarshal(m.Value, &event); err != nil {
				log.Printf("[UserEventConsumer ERROR] Unmarshal error: %v", err)
				_ = c.reader.CommitMessages(ctx, m)
				continue
			}

			log.Printf("[UserEventConsumer] Received UserRegisteredEvent: userId=%s, username=%s",
				event.UserID, event.Username)

			userID, err := uuid.Parse(event.UserID)
			if err != nil {
				log.Printf("[UserEventConsumer ERROR] Invalid UUID in event: %s", event.UserID)
				_ = c.reader.CommitMessages(ctx, m)
				continue
			}

			if _, err := c.svc.CreateProfile(ctx, userID, event.Username, event.Email); err != nil {
				log.Printf("[UserEventConsumer ERROR] Failed creating profile for %s: %v", event.UserID, err)
			}

			_ = c.reader.CommitMessages(ctx, m)
		}
	}()
}

func (c *UserEventConsumer) Close() error {
	log.Println("[UserEventConsumer] Closing Kafka reader...")
	return c.reader.Close()
}
