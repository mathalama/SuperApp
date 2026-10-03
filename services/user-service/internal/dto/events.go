package dto

type UserRegisteredEvent struct {
	EventID      string `json:"eventId"`
	UserID       string `json:"userId"`
	Username     string `json:"username"`
	Email        string `json:"email"`
	AuthProvider string `json:"authProvider"`
	Timestamp    int64  `json:"timestamp"`
}

type KycStatusChangedEvent struct {
	UserID        string      `json:"userId"`
	ApplicationID string      `json:"applicationId"`
	Status        string      `json:"status"`
	Reason        string      `json:"reason,omitempty"`
	Timestamp     interface{} `json:"timestamp,omitempty"`
}
