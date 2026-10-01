package proxy

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestReverseProxyStripPrefix(t *testing.T) {
	var downstreamReceivedPath string

	backendServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		downstreamReceivedPath = r.URL.Path
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"docs":"ok"}`))
	}))
	defer backendServer.Close()

	proxyHandler, err := NewReverseProxy(backendServer.URL, "/identity")
	if err != nil {
		t.Fatalf("Failed to create reverse proxy: %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/identity/v3/api-docs/swagger-config", nil)
	rec := httptest.NewRecorder()

	proxyHandler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("Expected 200 OK, got %d", rec.Code)
	}
	if downstreamReceivedPath != "/v3/api-docs/swagger-config" {
		t.Errorf("Expected downstream path '/v3/api-docs/swagger-config', got %q", downstreamReceivedPath)
	}
}

func TestReverseProxyDirectForwarding(t *testing.T) {
	var downstreamReceivedPath string
	var downstreamUserId string

	backendServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		downstreamReceivedPath = r.URL.Path
		downstreamUserId = r.Header.Get("X-User-Id")
		w.WriteHeader(http.StatusOK)
	}))
	defer backendServer.Close()

	proxyHandler, err := NewReverseProxy(backendServer.URL, "")
	if err != nil {
		t.Fatalf("Failed to create reverse proxy: %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/api/users/profile", nil)
	req.Header.Set("X-User-Id", "usr-42")
	rec := httptest.NewRecorder()

	proxyHandler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("Expected 200 OK, got %d", rec.Code)
	}
	if downstreamReceivedPath != "/api/users/profile" {
		t.Errorf("Expected downstream path '/api/users/profile', got %q", downstreamReceivedPath)
	}
	if downstreamUserId != "usr-42" {
		t.Errorf("Expected X-User-Id 'usr-42', got %q", downstreamUserId)
	}
}
