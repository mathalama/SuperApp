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
	kycStatusTmpl     *template.Template
)

func init() {
	welcomeTmpl = template.Must(template.ParseFS(templatesFS, "welcome-email.html"))
	verificationTmpl = template.Must(template.ParseFS(templatesFS, "verification-email.html"))
	resetPasswordTmpl = template.Must(template.ParseFS(templatesFS, "reset-password-email.html"))
	kycStatusTmpl = template.Must(template.ParseFS(templatesFS, "kyc-status-email.html"))
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

type KycStatusData struct {
	Status         string
	HeaderGradient string
	BadgeColor     string
	BadgeBg        string
	Message        string
	Reason         string
	DashboardURL   string
}

func RenderKycStatus(status, reason, dashboardURL string) (string, error) {
	var (
		headerGrad string
		badgeColor string
		badgeBg    string
		msg        string
	)

	switch status {
	case "VERIFIED":
		headerGrad = "linear-gradient(135deg, #059669, #10b981)"
		badgeColor = "#065f46"
		badgeBg = "#d1fae5"
		msg = "Congratulations! Your identity documents and biometric verification have been successfully validated. Full platform features and limits are now unlocked on your account."
	case "REJECTED":
		headerGrad = "linear-gradient(135deg, #dc2626, #ef4444)"
		badgeColor = "#991b1b"
		badgeBg = "#fee2e2"
		msg = "Your identity verification could not be approved at this time. Please review the details below, ensure your uploaded documents are clear and unexpired, and resubmit."
	case "MANUAL_REVIEW":
		headerGrad = "linear-gradient(135deg, #d97706, #f59e0b)"
		badgeColor = "#92400e"
		badgeBg = "#fef3c7"
		msg = "Your application has been received and routed to our compliance review team. No further action is required from you at this moment. You will be notified once the review is completed."
	default:
		headerGrad = "linear-gradient(135deg, #4f46e5, #6366f1)"
		badgeColor = "#3730a3"
		badgeBg = "#e0e7ff"
		msg = "Your verification status has been updated."
	}

	var buf bytes.Buffer
	err := kycStatusTmpl.Execute(&buf, KycStatusData{
		Status:         status,
		HeaderGradient: headerGrad,
		BadgeColor:     badgeColor,
		BadgeBg:        badgeBg,
		Message:        msg,
		Reason:         reason,
		DashboardURL:   dashboardURL,
	})
	if err != nil {
		return "", fmt.Errorf("render kyc status: %w", err)
	}
	return buf.String(), nil
}
