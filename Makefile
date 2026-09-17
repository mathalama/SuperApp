# ==============================================================================
# SuperApp Monorepo - Automation & Workflow Makefile
# ==============================================================================

.DEFAULT_GOAL := help
SHELL := /bin/bash

# ANSI Color codes for clean output
BLUE   := \033[36m
GREEN  := \033[32m
YELLOW := \033[33m
RED    := \033[31m
BOLD   := \033[1m
RESET  := \033[0m

JAVA_SERVICES := api-gateway identity-service kyc-service notification-service user-service

.PHONY: help
help: ## Display this help screen
	@echo -e ""
	@echo -e "${BOLD}SuperApp Monorepo Management${RESET}"
	@echo -e "${YELLOW}Usage:${RESET} make ${GREEN}<target>${RESET}"
	@echo -e ""
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  ${GREEN}%-20s${RESET} %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo -e ""

# ==============================================================================
# ENVIRONMENT & SETUP
# ==============================================================================

.PHONY: setup
setup: ## Install pre-commit and pre-push Git hooks
	@echo -e "${BLUE}==>${RESET} Setting up pre-commit and pre-push Git hooks..."
	@pre-commit install --hook-type pre-commit --hook-type pre-push
	@echo -e "${GREEN}✓ Git hooks installed successfully.${RESET}"

.PHONY: check-env
check-env: ## Verify that .env file exists
	@if [ ! -f .env ]; then \
		echo -e "${RED}✗ .env file not found! Copy .env.example to .env first.${RESET}"; \
		exit 1; \
	else \
		echo -e "${GREEN}✓ .env file found.${RESET}"; \
	fi

# ==============================================================================
# DOCKER COMPOSE & INFRASTRUCTURE
# ==============================================================================

.PHONY: up
up: check-env ## Build and start all services in detached mode
	@echo -e "${BLUE}==>${RESET} Starting all services..."
	docker compose up -d --build

.PHONY: down
down: ## Stop and remove all containers and networks
	@echo -e "${BLUE}==>${RESET} Stopping all services..."
	docker compose down

.PHONY: restart
restart: down up ## Restart all services

.PHONY: ps
ps: ## List running containers and health status
	@docker compose ps

.PHONY: logs
logs: ## Follow logs for all services (or use s=<service-name>)
	@if [ -n "$(s)" ]; then \
		docker compose logs -f $(s); \
	else \
		docker compose logs -f; \
	fi

.PHONY: infra-up
infra-up: check-env ## Start only infrastructure (Postgres, Redis, Kafka, MinIO, Zipkin)
	@echo -e "${BLUE}==>${RESET} Starting backing infrastructure..."
	docker compose up -d postgres redis kafka minio zipkin

.PHONY: infra-down
infra-down: ## Stop backing infrastructure
	@echo -e "${BLUE}==>${RESET} Stopping backing infrastructure..."
	docker compose stop postgres redis kafka minio zipkin

.PHONY: migrate
migrate: check-env ## Run Flyway database migrations for all services
	@echo -e "${BLUE}==>${RESET} Running database migrations..."
	docker compose run --rm migrate-identity
	docker compose run --rm migrate-user
	docker compose run --rm migrate-kyc
	@echo -e "${GREEN}✓ All database migrations applied.${RESET}"

# ==============================================================================
# JAVA MICROSERVICES (BUILD, TEST, CLEAN)
# ==============================================================================

.PHONY: build
build: ## Compile and build all Java microservices
	@for svc in $(JAVA_SERVICES); do \
		echo -e "${BLUE}==>${RESET} Building $$svc..."; \
		(cd services/$$svc && (./gradlew classes || ./gradlew.bat classes)) || exit 1; \
	done
	@echo -e "${GREEN}✓ All microservices built successfully.${RESET}"

.PHONY: test
test: ## Run unit and integration tests across all Java microservices
	@for svc in $(JAVA_SERVICES); do \
		echo -e "${BLUE}==>${RESET} Testing $$svc..."; \
		(cd services/$$svc && (./gradlew test || ./gradlew.bat test)) || exit 1; \
	done
	@echo -e "${GREEN}✓ All microservice tests passed.${RESET}"

.PHONY: clean
clean: ## Clean build directories, caches, and logs across all services
	@for svc in $(JAVA_SERVICES); do \
		echo -e "${YELLOW}==>${RESET} Cleaning $$svc..."; \
		(cd services/$$svc && (./gradlew clean || ./gradlew.bat clean)) 2>/dev/null || true; \
		rm -rf services/$$svc/.gradle services/$$svc/build services/$$svc/bin; \
	done
	@rm -f services/*/*.log services/*/*.iml
	@echo -e "${GREEN}✓ Clean completed.${RESET}"

# ==============================================================================
# CODE QUALITY & SECURITY
# ==============================================================================

.PHONY: lint
lint: ## Run pre-commit hooks manually against all files
	@echo -e "${BLUE}==>${RESET} Running pre-commit linters and validators..."
	@pre-commit run --all-files

.PHONY: gitleaks
gitleaks: ## Scan repository for hardcoded secrets with Gitleaks
	@echo -e "${BLUE}==>${RESET} Running Gitleaks secret detection..."
	@pre-commit run gitleaks --all-files

# ==============================================================================
# KUBERNETES DEPLOYMENT
# ==============================================================================

.PHONY: k8s-apply
k8s-apply: ## Apply all Kubernetes manifests in order
	@echo -e "${BLUE}==>${RESET} Applying Kubernetes manifests..."
	kubectl apply -f k8s/00-secrets.yaml
	kubectl apply -f k8s/01-configmap.yaml
	kubectl apply -f k8s/02-postgres.yaml
	kubectl apply -f k8s/03-redis.yaml
	kubectl apply -f k8s/04-kafka.yaml
	kubectl apply -f k8s/05-infra.yaml
	kubectl apply -f k8s/06-services.yaml
	@echo -e "${GREEN}✓ Kubernetes manifests applied.${RESET}"

.PHONY: k8s-delete
k8s-delete: ## Delete all Kubernetes resources
	@echo -e "${RED}==>${RESET} Deleting Kubernetes resources..."
	kubectl delete -f k8s/06-services.yaml --ignore-not-found
	kubectl delete -f k8s/05-infra.yaml --ignore-not-found
	kubectl delete -f k8s/04-kafka.yaml --ignore-not-found
	kubectl delete -f k8s/03-redis.yaml --ignore-not-found
	kubectl delete -f k8s/02-postgres.yaml --ignore-not-found
	kubectl delete -f k8s/01-configmap.yaml --ignore-not-found
	kubectl delete -f k8s/00-secrets.yaml --ignore-not-found
	@echo -e "${GREEN}✓ Kubernetes resources deleted.${RESET}"

.PHONY: k8s-status
k8s-status: ## Show Kubernetes Pods, Services, and Deployments
	@kubectl get pods,svc,deploy

# ==============================================================================
# FRONTEND & MOBILE APP
# ==============================================================================

.PHONY: mobile-run
mobile-run: ## Run Flutter mobile app in debug mode
	@echo -e "${BLUE}==>${RESET} Running Flutter Mobile App..."
	@(cd mobile && flutter run)

.PHONY: mobile-test
mobile-test: ## Run Flutter mobile app unit tests
	@echo -e "${BLUE}==>${RESET} Running Flutter Mobile Tests..."
	@(cd mobile && flutter test)

.PHONY: web-dev
web-dev: ## Run KYC Web Frontend locally
	@echo -e "${BLUE}==>${RESET} Starting KYC Web dev server..."
	@(cd web/kyc-web && npm install && npm run dev)
