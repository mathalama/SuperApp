package service

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"sync"
	"time"
)

type SSEHub struct {
	mu      sync.RWMutex
	clients map[string]map[chan string]bool
}

func NewSSEHub() *SSEHub {
	return &SSEHub{
		clients: make(map[string]map[chan string]bool),
	}
}

func (h *SSEHub) Subscribe(userId string) chan string {
	h.mu.Lock()
	defer h.mu.Unlock()

	ch := make(chan string, 10)
	if _, ok := h.clients[userId]; !ok {
		h.clients[userId] = make(map[chan string]bool)
	}
	h.clients[userId][ch] = true
	log.Printf("[SSE Hub] User %s subscribed to live notification stream (active: %d)", userId, len(h.clients[userId]))
	return ch
}

func (h *SSEHub) Unsubscribe(userId string, ch chan string) {
	h.mu.Lock()
	defer h.mu.Unlock()

	if userChans, ok := h.clients[userId]; ok {
		delete(userChans, ch)
		close(ch)
		if len(userChans) == 0 {
			delete(h.clients, userId)
		}
	}
	log.Printf("[SSE Hub] User %s unsubscribed from live notification stream", userId)
}

func (h *SSEHub) BroadcastToUser(userId string, eventType string, payload interface{}) {
	h.mu.RLock()
	defer h.mu.RUnlock()

	userChans, ok := h.clients[userId]
	if !ok || len(userChans) == 0 {
		return
	}

	data, err := json.Marshal(map[string]interface{}{
		"type":      eventType,
		"timestamp": time.Now().UTC().Format(time.RFC3339),
		"payload":   payload,
	})
	if err != nil {
		return
	}

	msg := fmt.Sprintf("event: %s\ndata: %s\n\n", eventType, string(data))

	for ch := range userChans {
		select {
		case ch <- msg:
		default:
			log.Printf("[SSE Hub WARN] User %s buffer full, dropping message", userId)
		}
	}
}

func (h *SSEHub) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	userId := r.Header.Get("X-User-Id")
	if userId == "" {
		userId = r.URL.Query().Get("userId")
	}
	if userId == "" {
		http.Error(w, "Unauthorized: missing user identifier", http.StatusUnauthorized)
		return
	}

	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "Streaming unsupported", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	msgChan := h.Subscribe(userId)
	defer h.Unsubscribe(userId, msgChan)

	// Send initial connect handshake
	_, _ = fmt.Fprintf(w, "event: connected\ndata: {\"status\":\"connected\",\"userId\":\"%s\"}\n\n", userId)
	flusher.Flush()

	ticker := time.NewTicker(15 * time.Second)
	defer ticker.Stop()

	notify := r.Context().Done()
	for {
		select {
		case <-notify:
			return
		case <-ticker.C:
			_, _ = fmt.Fprintf(w, ": keepalive\n\n")
			flusher.Flush()
		case msg, ok := <-msgChan:
			if !ok {
				return
			}
			_, _ = fmt.Fprint(w, msg)
			flusher.Flush()
		}
	}
}
