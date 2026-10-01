package templates

import (
	"strings"
	"testing"
)

func TestRenderWelcome(t *testing.T) {
	html, err := RenderWelcome("alex", "http://localhost:3000/dashboard")
	if err != nil {
		t.Fatalf("RenderWelcome error: %v", err)
	}

	if !strings.Contains(html, "alex") {
		t.Errorf("Expected html to contain username 'alex', got: %s", html)
	}
	if !strings.Contains(html, "http://localhost:3000/dashboard") {
		t.Errorf("Expected html to contain dashboard link, got: %s", html)
	}
	if !strings.Contains(html, "Welcome to SuperApp!") {
		t.Errorf("Expected html to contain header text, got: %s", html)
	}
}

func TestRenderVerification(t *testing.T) {
	html, err := RenderVerification("johndoe", "987654")
	if err != nil {
		t.Fatalf("RenderVerification error: %v", err)
	}

	if !strings.Contains(html, "johndoe") {
		t.Errorf("Expected html to contain 'johndoe', got: %s", html)
	}
	if !strings.Contains(html, "987654") {
		t.Errorf("Expected html to contain code '987654', got: %s", html)
	}
}

func TestRenderResetPassword(t *testing.T) {
	resetLink := "http://localhost:3000/reset-password?token=secret123"
	html, err := RenderResetPassword("alice", resetLink)
	if err != nil {
		t.Fatalf("RenderResetPassword error: %v", err)
	}

	if !strings.Contains(html, "alice") {
		t.Errorf("Expected html to contain 'alice', got: %s", html)
	}
	if !strings.Contains(html, resetLink) {
		t.Errorf("Expected html to contain resetLink, got: %s", html)
	}
}
