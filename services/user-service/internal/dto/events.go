package dto

type UserRegisteredEvent struct {
	EventID      string `json:"eventId"`
	UserID       string `json:"userId"`
	Username     string `json:"username"`
	Email        string `json:"email"`
	AuthProvider string `json:"authProvider"`
	Timestamp    int64  `json:"timestamp"`
}
