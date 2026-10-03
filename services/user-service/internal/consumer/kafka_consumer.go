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
	cfg                  *config.Config
	svc                  *service.UserProfileService
	userRegisteredReader *kafka.Reader
	kycEventsReader      *kafka.Reader
}

func NewUserEventConsumer(cfg *config.Config, svc *service.UserProfileService) *UserEventConsumer {
	brokers := strings.Split(cfg.KafkaBootstrapServers, ",")

	initReader := func(topic string) *kafka.Reader {
		return kafka.NewReader(kafka.ReaderConfig{
			Brokers:        brokers,
			GroupID:        cfg.KafkaGroupID,
			Topic:          topic,
			MinBytes:       10e3, // 10KB
			MaxBytes:       10e6, // 10MB
			CommitInterval: 1 * time.Second,
			StartOffset:    kafka.FirstOffset,
		})
	}

	return &UserEventConsumer{
		cfg:                  cfg,
		svc:                  svc,
		userRegisteredReader: initReader(cfg.KafkaTopicUserRegistered),
		kycEventsReader:      initReader(cfg.KafkaTopicKycEvents),
	}
}

func (c *UserEventConsumer) Start(ctx context.Context) {
	log.Printf("[UserEventConsumer] Starting consumers on topics '%s' and '%s' (group: '%s')",
		c.cfg.KafkaTopicUserRegistered, c.cfg.KafkaTopicKycEvents, c.cfg.KafkaGroupID)

	go c.consumeUserRegistered(ctx)
	go c.consumeKycEvents(ctx)
}

func (c *UserEventConsumer) consumeUserRegistered(ctx context.Context) {
	for {
		m, err := c.userRegisteredReader.FetchMessage(ctx)
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
			log.Printf("[UserEventConsumer ERROR] Unmarshal user event error: %v", err)
			_ = c.userRegisteredReader.CommitMessages(ctx, m)
			continue
		}

		log.Printf("[UserEventConsumer] Received UserRegisteredEvent: userId=%s, username=%s",
			event.UserID, event.Username)

		userID, err := uuid.Parse(event.UserID)
		if err != nil {
			log.Printf("[UserEventConsumer ERROR] Invalid UUID in event: %s", event.UserID)
			_ = c.userRegisteredReader.CommitMessages(ctx, m)
			continue
		}

		if _, err := c.svc.CreateProfile(ctx, userID, event.Username, event.Email); err != nil {
			log.Printf("[UserEventConsumer ERROR] Failed creating profile for %s: %v", event.UserID, err)
		}

		_ = c.userRegisteredReader.CommitMessages(ctx, m)
	}
}

func (c *UserEventConsumer) consumeKycEvents(ctx context.Context) {
	for {
		m, err := c.kycEventsReader.FetchMessage(ctx)
		if err != nil {
			if errors.Is(err, context.Canceled) {
				return
			}
			log.Printf("[UserEventConsumer Error] Fetch %s: %v", c.cfg.KafkaTopicKycEvents, err)
			time.Sleep(1 * time.Second)
			continue
		}

		var event dto.KycStatusChangedEvent
		if err := json.Unmarshal(m.Value, &event); err != nil {
			log.Printf("[UserEventConsumer ERROR] Unmarshal KYC event error: %v", err)
			_ = c.kycEventsReader.CommitMessages(ctx, m)
			continue
		}

		log.Printf("[UserEventConsumer] Processing KYC status update: userId=%s, status=%s",
			event.UserID, event.Status)

		userID, err := uuid.Parse(event.UserID)
		if err != nil {
			log.Printf("[UserEventConsumer ERROR] Invalid UUID in KYC event: %s", event.UserID)
			_ = c.kycEventsReader.CommitMessages(ctx, m)
			continue
		}

		if _, err := c.svc.UpdateKycStatus(ctx, userID, event.Status); err != nil {
			log.Printf("[UserEventConsumer ERROR] Failed updating KYC status for %s: %v", event.UserID, err)
		}

		_ = c.kycEventsReader.CommitMessages(ctx, m)
	}
}

func (c *UserEventConsumer) Close() error {
	log.Println("[UserEventConsumer] Closing Kafka readers...")
	err1 := c.userRegisteredReader.Close()
	err2 := c.kycEventsReader.Close()
	if err1 != nil {
		return err1
	}
	return err2
}
