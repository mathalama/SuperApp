package dto

type UserRegisteredEvent struct {
	EventID      string `json:"eventId"`
	UserID       string `json:"userId"`
	Username     string `json:"username"`
	Email        string `json:"email"`
	AuthProvider string `json:"authProvider"`
	Timestamp    int64  `json:"timestamp"`
}

type VerificationEmailRequestedEvent struct {
	EventID           string `json:"eventId"`
	Email             string `json:"email"`
	Username          string `json:"username"`
	VerificationToken string `json:"verificationToken"`
	Timestamp         int64  `json:"timestamp"`
}

type PasswordResetEmailRequestedEvent struct {
	EventID    string `json:"eventId"`
	Email      string `json:"email"`
	Username   string `json:"username"`
	ResetToken string `json:"resetToken"`
	Timestamp  int64  `json:"timestamp"`
}

type KycStatusChangedEvent struct {
	UserID        string `json:"userId"`
	ApplicationID string `json:"applicationId"`
	Email         string `json:"email"`
	Status        string `json:"status"`
	Reason        string `json:"reason"`
	Timestamp     string `json:"timestamp"`
}
