package main

import (
	"log"

	"dev.mathalama/notificationservice/internal/app"
	"dev.mathalama/notificationservice/internal/config"
)

func main() {
	cfg := config.Load()

	application, err := app.New(cfg)
	if err != nil {
		log.Fatalf("[Notification FATAL] Initialization failed: %v", err)
	}

	if err := application.Run(); err != nil {
		log.Fatalf("[Notification FATAL] Application error: %v", err)
	}
}
