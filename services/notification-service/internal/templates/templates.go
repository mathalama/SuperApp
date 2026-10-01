package templates

import (
	"bytes"
	"embed"
	"fmt"
	"html/template"
)

//go:embed *.html
var templatesFS embed.FS

var (
	welcomeTmpl       *template.Template
	verificationTmpl  *template.Template
	resetPasswordTmpl *template.Template
)

func init() {
	welcomeTmpl = template.Must(template.ParseFS(templatesFS, "welcome-email.html"))
	verificationTmpl = template.Must(template.ParseFS(templatesFS, "verification-email.html"))
	resetPasswordTmpl = template.Must(template.ParseFS(templatesFS, "reset-password-email.html"))
}

type WelcomeData struct {
	Username     string
	DashboardURL string
}

type VerificationData struct {
	Username         string
	VerificationCode string
}

type ResetPasswordData struct {
	Username  string
	ResetLink string
}

func RenderWelcome(username, dashboardURL string) (string, error) {
	var buf bytes.Buffer
	err := welcomeTmpl.Execute(&buf, WelcomeData{
		Username:     username,
		DashboardURL: dashboardURL,
	})
	if err != nil {
		return "", fmt.Errorf("render welcome: %w", err)
	}
	return buf.String(), nil
}

func RenderVerification(username, code string) (string, error) {
	var buf bytes.Buffer
	err := verificationTmpl.Execute(&buf, VerificationData{
		Username:         username,
		VerificationCode: code,
	})
	if err != nil {
		return "", fmt.Errorf("render verification: %w", err)
	}
	return buf.String(), nil
}

func RenderResetPassword(username, resetLink string) (string, error) {
	var buf bytes.Buffer
	err := resetPasswordTmpl.Execute(&buf, ResetPasswordData{
		Username:  username,
		ResetLink: resetLink,
	})
	if err != nil {
		return "", fmt.Errorf("render reset password: %w", err)
	}
	return buf.String(), nil
}
