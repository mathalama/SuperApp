package main

import (
	"log"

	"dev.mathalama/userservice/internal/app"
	"dev.mathalama/userservice/internal/config"
)

func main() {
	log.Println("[Main] Starting User Service (Go / Chi)...")

	cfg := config.Load()
	application, err := app.NewApp(cfg)
	if err != nil {
		log.Fatalf("[Main] Failed to initialize application: %v", err)
	}

	if err := application.Run(); err != nil {
		log.Fatalf("[Main] Application terminated with error: %v", err)
	}
}
