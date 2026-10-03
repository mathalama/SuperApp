package consumer

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"strings"
	"time"

	"dev.mathalama/notificationservice/internal/config"
	"dev.mathalama/notificationservice/internal/dto"
	"dev.mathalama/notificationservice/internal/service"

	"github.com/segmentio/kafka-go"
)

type NotificationConsumer struct {
	cfg         *config.Config
	emailSvc    *service.EmailService
	idemSvc     *service.IdempotencyService
	sseHub      *service.SSEHub
	readers     []*kafka.Reader
	dltWriter   *kafka.Writer
}

func NewNotificationConsumer(
	cfg *config.Config,
	emailSvc *service.EmailService,
	idemSvc *service.IdempotencyService,
	sseHub *service.SSEHub,
) *NotificationConsumer {
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

	readers := []*kafka.Reader{
		initReader(cfg.KafkaTopicUserRegistered),
		initReader(cfg.KafkaTopicVerificationEmail),
		initReader(cfg.KafkaTopicPasswordReset),
		initReader(cfg.KafkaTopicKycEvents),
	}

	dltWriter := &kafka.Writer{
		Addr:         kafka.TCP(brokers...),
		Balancer:     &kafka.LeastBytes{},
		BatchTimeout: 10 * time.Millisecond,
	}

	return &NotificationConsumer{
		cfg:       cfg,
		emailSvc:  emailSvc,
		idemSvc:   idemSvc,
		readers:   readers,
		dltWriter: dltWriter,
	}
}

func (c *NotificationConsumer) Start(ctx context.Context) {
	log.Printf("[NotificationConsumer] Starting Kafka consumers on brokers: %s (group: %s)",
		c.cfg.KafkaBootstrapServers, c.cfg.KafkaGroupID)

	// Launch consumer goroutine for each topic
	go c.consumeUserRegistered(ctx, c.readers[0])
	go c.consumeVerificationEmail(ctx, c.readers[1])
	go c.consumePasswordReset(ctx, c.readers[2])
	go c.consumeKycEvents(ctx, c.readers[3])
}

func (c *NotificationConsumer) Close() error {
	log.Println("[NotificationConsumer] Closing Kafka readers and writer...")
	var errs []string
	for _, r := range c.readers {
		if err := r.Close(); err != nil {
			errs = append(errs, err.Error())
		}
	}
	if err := c.dltWriter.Close(); err != nil {
		errs = append(errs, err.Error())
	}
	if len(errs) > 0 {
		return errors.New(strings.Join(errs, "; "))
	}
	return nil
}

func (c *NotificationConsumer) consumeUserRegistered(ctx context.Context, reader *kafka.Reader) {
	for {
		m, err := reader.FetchMessage(ctx)
		if err != nil {
			if errors.Is(err, context.Canceled) {
				return
			}
			log.Printf("[Consumer Error] Fetch %s: %v", reader.Config().Topic, err)
			time.Sleep(1 * time.Second)
			continue
		}

		var event dto.UserRegisteredEvent
		if err := json.Unmarshal(m.Value, &event); err != nil {
			log.Printf("[Consumer ERROR] Unmarshal user registered event: %v", err)
			c.sendToDLT(ctx, m.Topic, m.Key, m.Value, err.Error())
			_ = reader.CommitMessages(ctx, m)
			continue
		}

		if !c.idemSvc.MarkIfNew(ctx, event.EventID) {
			log.Printf("[Idempotency] Duplicate event skipped: eventId=%s", event.EventID)
			_ = reader.CommitMessages(ctx, m)
			continue
		}

		log.Printf("[Consumer] Processing registration for %s (%s)", event.Username, event.Email)
		if err := c.emailSvc.SendWelcomeEmail(event.Email, event.Username); err != nil {
			c.idemSvc.Remove(ctx, event.EventID)
			log.Printf("[Consumer ERROR] Failed sending welcome email: %v", err)
			c.sendToDLT(ctx, m.Topic, m.Key, m.Value, err.Error())
		}

		_ = reader.CommitMessages(ctx, m)
	}
}

func (c *NotificationConsumer) consumeVerificationEmail(ctx context.Context, reader *kafka.Reader) {
	for {
		m, err := reader.FetchMessage(ctx)
		if err != nil {
			if errors.Is(err, context.Canceled) {
				return
			}
			log.Printf("[Consumer Error] Fetch %s: %v", reader.Config().Topic, err)
			time.Sleep(1 * time.Second)
			continue
		}

		var event dto.VerificationEmailRequestedEvent
		if err := json.Unmarshal(m.Value, &event); err != nil {
			log.Printf("[Consumer ERROR] Unmarshal verification email event: %v", err)
			c.sendToDLT(ctx, m.Topic, m.Key, m.Value, err.Error())
			_ = reader.CommitMessages(ctx, m)
			continue
		}

		if !c.idemSvc.MarkIfNew(ctx, event.EventID) {
			log.Printf("[Idempotency] Duplicate event skipped: eventId=%s", event.EventID)
			_ = reader.CommitMessages(ctx, m)
			continue
		}

		log.Printf("[Consumer] Processing verification email for %s (%s)", event.Username, event.Email)
		if err := c.emailSvc.SendVerificationEmail(event.Email, event.Username, event.VerificationToken); err != nil {
			c.idemSvc.Remove(ctx, event.EventID)
			log.Printf("[Consumer ERROR] Failed sending verification email: %v", err)
			c.sendToDLT(ctx, m.Topic, m.Key, m.Value, err.Error())
		}

		_ = reader.CommitMessages(ctx, m)
	}
}

func (c *NotificationConsumer) consumePasswordReset(ctx context.Context, reader *kafka.Reader) {
	for {
		m, err := reader.FetchMessage(ctx)
		if err != nil {
			if errors.Is(err, context.Canceled) {
				return
			}
			log.Printf("[Consumer Error] Fetch %s: %v", reader.Config().Topic, err)
			time.Sleep(1 * time.Second)
			continue
		}

		var event dto.PasswordResetEmailRequestedEvent
		if err := json.Unmarshal(m.Value, &event); err != nil {
			log.Printf("[Consumer ERROR] Unmarshal password reset event: %v", err)
			c.sendToDLT(ctx, m.Topic, m.Key, m.Value, err.Error())
			_ = reader.CommitMessages(ctx, m)
			continue
		}

		if !c.idemSvc.MarkIfNew(ctx, event.EventID) {
			log.Printf("[Idempotency] Duplicate event skipped: eventId=%s", event.EventID)
			_ = reader.CommitMessages(ctx, m)
			continue
		}

		log.Printf("[Consumer] Processing password reset email for %s (%s)", event.Username, event.Email)
		if err := c.emailSvc.SendPasswordResetEmail(event.Email, event.Username, event.ResetToken); err != nil {
			c.idemSvc.Remove(ctx, event.EventID)
			log.Printf("[Consumer ERROR] Failed sending password reset email: %v", err)
			c.sendToDLT(ctx, m.Topic, m.Key, m.Value, err.Error())
		}

		_ = reader.CommitMessages(ctx, m)
	}
}

func (c *NotificationConsumer) consumeKycEvents(ctx context.Context, reader *kafka.Reader) {
	for {
		m, err := reader.FetchMessage(ctx)
		if err != nil {
			if errors.Is(err, context.Canceled) {
				return
			}
			log.Printf("[Consumer Error] Fetch %s: %v", reader.Config().Topic, err)
			time.Sleep(1 * time.Second)
			continue
		}

		var event dto.KycStatusChangedEvent
		if err := json.Unmarshal(m.Value, &event); err != nil {
			log.Printf("[Consumer ERROR] Unmarshal KYC status changed event: %v", err)
			c.sendToDLT(ctx, m.Topic, m.Key, m.Value, err.Error())
			_ = reader.CommitMessages(ctx, m)
			continue
		}

		eventId := "kyc-" + event.ApplicationID + "-" + event.Status
		if !c.idemSvc.MarkIfNew(ctx, eventId) {
			log.Printf("[Idempotency] Duplicate KYC event skipped: %s", eventId)
			_ = reader.CommitMessages(ctx, m)
			continue
		}

		log.Printf("[Consumer] Processing KYC status update: user=%s, email=%s, app=%s, status=%s, reason=%s",
			event.UserID, event.Email, event.ApplicationID, event.Status, event.Reason)

		if c.sseHub != nil && event.UserID != "" {
			c.sseHub.BroadcastToUser(event.UserID, "KYC_STATUS_CHANGED", event)
		}

		if event.Email != "" && (event.Status == "VERIFIED" || event.Status == "REJECTED" || event.Status == "MANUAL_REVIEW") {
			if err := c.emailSvc.SendKycStatusEmail(event.Email, event.Status, event.Reason); err != nil {
				log.Printf("[Consumer ERROR] Failed sending KYC status email to %s: %v", event.Email, err)
			} else {
				log.Printf("[Consumer] KYC status email successfully sent to %s (status: %s)", event.Email, event.Status)
			}
		}

		_ = reader.CommitMessages(ctx, m)
	}
}


func (c *NotificationConsumer) sendToDLT(ctx context.Context, originalTopic string, key, value []byte, reason string) {
	dltTopic := originalTopic + ".DLT"
	log.Printf("[DLT] Routing failed message to %s (reason: %s)", dltTopic, reason)

	msg := kafka.Message{
		Topic: dltTopic,
		Key:   key,
		Value: value,
		Headers: []kafka.Header{
			{Key: "DLT_ORIGINAL_TOPIC", Value: []byte(originalTopic)},
			{Key: "DLT_EXCEPTION_MESSAGE", Value: []byte(reason)},
		},
		Time: time.Now(),
	}

	publishCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()

	if err := c.dltWriter.WriteMessages(publishCtx, msg); err != nil {
		log.Printf("[DLT CRITICAL] Failed to write message to DLT topic %s: %v", dltTopic, err)
	}
}
