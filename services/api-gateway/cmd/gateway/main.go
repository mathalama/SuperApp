package main

import (
	"log"

	"dev.mathalama/apigateway/internal/app"
	"dev.mathalama/apigateway/internal/config"
)

func main() {
	cfg := config.Load()

	application, err := app.New(cfg)
	if err != nil {
		log.Fatalf("[Gateway FATAL] Initialization failed: %v", err)
	}

	if err := application.Run(); err != nil {
		log.Fatalf("[Gateway FATAL] Application runtime error: %v", err)
	}
}
