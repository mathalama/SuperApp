package service_test

import (
	"testing"
	"time"

	"dev.mathalama/notificationservice/internal/service"
)

func TestSSEHub_SubscribeBroadcastUnsubscribe(t *testing.T) {
	hub := service.NewSSEHub()
	userID := "user-123"

	ch := hub.Subscribe(userID)
	if ch == nil {
		t.Fatal("expected non-nil channel")
	}

	payload := map[string]string{"status": "APPROVED"}
	hub.BroadcastToUser(userID, "KYC_STATUS_CHANGED", payload)

	select {
	case msg := <-ch:
		if len(msg) == 0 {
			t.Fatal("expected non-empty message")
		}
	case <-time.After(1 * time.Second):
		t.Fatal("timed out waiting for broadcast message")
	}

	hub.Unsubscribe(userID, ch)
}
