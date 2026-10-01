package service

import (
	"fmt"
	"log"
	"net"
	"net/smtp"
	"strings"

	"dev.mathalama/notificationservice/internal/config"
	"dev.mathalama/notificationservice/internal/templates"
)

type EmailService struct {
	cfg *config.Config
}

func NewEmailService(cfg *config.Config) *EmailService {
	return &EmailService{cfg: cfg}
}

func (s *EmailService) SendWelcomeEmail(toEmail, username string) error {
	log.Printf("[EmailService] Sending welcome email to %s (username: %s)", toEmail, username)
	html, err := templates.RenderWelcome(username, s.cfg.FrontendURL)
	if err != nil {
		return err
	}
	return s.sendMail(toEmail, "Welcome to SuperApp!", html, "SuperApp")
}

func (s *EmailService) SendVerificationEmail(toEmail, username, verificationCode string) error {
	log.Printf("[EmailService] Sending verification email to %s (code: %s)", toEmail, verificationCode)
	html, err := templates.RenderVerification(username, verificationCode)
	if err != nil {
		return err
	}
	return s.sendMail(toEmail, "Verify your email address", html, "Identity Service")
}

func (s *EmailService) SendPasswordResetEmail(toEmail, username, resetToken string) error {
	log.Printf("[EmailService] Sending password reset email to %s", toEmail)
	resetLink := fmt.Sprintf("%s/reset-password?token=%s", s.cfg.FrontendURL, resetToken)
	html, err := templates.RenderResetPassword(username, resetLink)
	if err != nil {
		return err
	}
	return s.sendMail(toEmail, "Password Reset Request", html, "Identity Service")
}

func (s *EmailService) sendMail(to, subject, htmlBody, fromName string) error {
	addr := net.JoinHostPort(s.cfg.MailHost, s.cfg.MailPort)
	from := s.cfg.MailUsername
	if from == "" {
		from = "noreply@superapp.dev"
	}

	header := make(map[string]string)
	header["From"] = fmt.Sprintf("%s <%s>", fromName, from)
	header["To"] = to
	header["Subject"] = subject
	header["MIME-Version"] = "1.0"
	header["Content-Type"] = "text/html; charset=UTF-8"

	var msg strings.Builder
	for k, v := range header {
		msg.WriteString(fmt.Sprintf("%s: %s\r\n", k, v))
	}
	msg.WriteString("\r\n")
	msg.WriteString(htmlBody)

	var auth smtp.Auth
	if s.cfg.MailPassword != "" {
		auth = smtp.PlainAuth("", s.cfg.MailUsername, s.cfg.MailPassword, s.cfg.MailHost)
	}

	err := smtp.SendMail(addr, auth, from, []string{to}, []byte(msg.String()))
	if err != nil {
		log.Printf("[EmailService ERROR] Failed to send email '%s' to %s via %s: %v", subject, to, addr, err)
		return err
	}
	log.Printf("[EmailService SUCCESS] Email '%s' successfully delivered to %s", subject, to)
	return nil
}
