package service

import (
	"context"
	"log"
	"time"

	"github.com/redis/go-redis/v9"
)

const (
	keyPrefix = "notification:processed:event:"
	ttl       = 24 * time.Hour
)

type IdempotencyService struct {
	rdb *redis.Client
}

func NewIdempotencyService(rdb *redis.Client) *IdempotencyService {
	return &IdempotencyService{rdb: rdb}
}

// MarkIfNew performs atomic SETNX in Redis with 24-hour TTL.
// Returns true if event is brand new, false if duplicate.
func (s *IdempotencyService) MarkIfNew(ctx context.Context, eventID string) bool {
	if eventID == "" {
		return true
	}
	if s.rdb == nil {
		return true
	}

	key := keyPrefix + eventID
	ok, err := s.rdb.SetNX(ctx, key, "PROCESSED", ttl).Result()
	if err != nil {
		log.Printf("[Idempotency WARN] Redis error on event %s: %v (allowing execution)", eventID, err)
		return true
	}
	return ok
}

// Remove removes the key from Redis in case processing failed and needs retry.
func (s *IdempotencyService) Remove(ctx context.Context, eventID string) {
	if eventID == "" || s.rdb == nil {
		return
	}
	_ = s.rdb.Del(ctx, keyPrefix+eventID).Err()
}
