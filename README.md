# SuperApp Monorepo

Enterprise-grade microservices monorepo powering a fintech/identity platform with automated AI-driven KYC verification, biometrics, user management, and multi-platform client applications.

---

## Architecture Overview

```
                      ┌──────────────────────────────┐
                      │    Clients (Web & Mobile)    │
                      │  • React / Vite (KYC Web)    │
                      │  • Flutter (Mobile App)      │
                      └──────────────┬───────────────┘
                                     │
                                     ▼ :8700
                      ┌──────────────────────────────┐
                      │      API Gateway (Spring)    │
                      │  • Route Matching            │
                      │  • JWT Authentication        │
                      │  • Rate Limiting (Redis)     │
                      └──────────────┬───────────────┘
                                     │
         ┌───────────────────────────┼──────────────────────────┐
         ▼                           ▼                          ▼
┌──────────────────┐       ┌──────────────────┐       ┌──────────────────┐
│ Identity Service │       │   User Service   │       │   KYC Service    │
│  • Auth / OAuth2 │       │  • User Profiles │       │  • Verification  │
│  • JWT Lifecycle │       │  • S3 Avatars    │       │  • State Machine │
│  • Session Mgmt  │       │  • Postgres / S3 │       │  • Orchestration │
└────────┬─────────┘       └────────┬─────────┘       └────────┬─────────┘
         │                          │                          │
         │                          │                          ▼ :8000
         │                          │                 ┌──────────────────┐
         │                          │                 │  KYC ML Service  │
         │                          │                 │  • EasyOCR / MRZ │
         │                          │                 │  • DeepFace 1:1  │
         │                          │                 │  • Liveness Test │
         │                          │                 └──────────────────┘
         │                          │                          │
         └──────────────────────────┼──────────────────────────┘
                                    │ Events
                                    ▼
                         ┌──────────────────────┐
                         │ Apache Kafka (Broker)│
                         └──────────┬───────────┘
                                    │ Consume Events
                                    ▼
                         ┌──────────────────────┐
                         │ Notification Service │
                         │  • Email Dispatcher  │
                         │  • Kafka Consumers   │
                         └──────────────────────┘
```

---

## Tech Stack

| Domain | Technologies |
|---|---|
| **Backend** | Java 21, Spring Boot 3.4, Spring Cloud Gateway, Spring Data JPA, Spring Security, Hibernate |
| **ML & Vision** | Python 3.11, FastAPI, EasyOCR, DeepFace (ArcFace), OpenCV, Torch, MRZ Reader |
| **Frontend** | React 18, Vite 6, TypeScript, Lucide Icons, Nginx |
| **Mobile** | Flutter 3 (iOS & Android) |
| **Data & Storage** | PostgreSQL 15, Redis 7, MinIO (S3-compatible object storage) |
| **Messaging** | Apache Kafka 3.9 (KRaft mode) |
| **Observability** | OpenZipkin (Distributed Tracing), Micrometer, Prometheus, Spring Actuator |
| **Containerization** | Docker, Docker Compose (modular v2/v5), Kubernetes |

---

## Port Mapping

| Service | Internal Port | Host Port | Description |
|---|---|---|---|
| **API Gateway** | `8010` | **`8700`** | Main entry point for all client requests |
| **KYC Web** | `3000` | **`3000`** | KYC Onboarding & Liveness Web Dashboard |
| **Identity Service** | `8080` | *internal* | Authentication, OAuth2 & Sessions |
| **User Service** | `8083` | *internal* | Profiles & Avatar management |
| **Notification Service** | `8084` | *internal* | Async notifications (SMTP / Kafka) |
| **KYC Service** | `8085` | *internal* | Business logic for KYC verification |
| **KYC ML Service** | `8000` | *internal* | Face recognition, passive liveness, OCR |
| **PostgreSQL** | `5432` | **`8500`** | Multi-DB (`identity_db`, `user_db`, `kyc_db`) |
| **Redis** | `6379` | **`8501`** | Token store, rate limits, caches |
| **Kafka Broker** | `9092` | **`8502`** | Event bus / messaging |
| **Zipkin** | `9411` | **`8503`** | Tracing UI |
| **MinIO API** | `9000` | **`8504`** | S3 API endpoint |
| **MinIO Console** | `9001` | **`8505`** | S3 Web Management UI |

---

## Quick Start

### 1. Prerequisites
- Docker Desktop (version 24+ with Compose v2.20+)
- JDK 21 (for local Java development)
- Node.js 20+ (for web development)
- Flutter SDK (for mobile development)

### 2. Configure Environment
Copy the template `.env.example` to `.env`:
```bash
cp .env.example .env
```

### 3. Launch with Make or Docker Compose

```bash
# Start all infrastructure, services, and web UI
make up
# or: docker compose up -d --build

# Run database migrations for all services
make migrate

# View health and running status
make ps

# Follow logs (all or specific service)
make logs
make logs s=identity-service
```

---

## Modular Compose Commands

You can run individual layers independently:

```bash
# 1. Backing infrastructure only (Postgres, Redis, Kafka, MinIO, Zipkin)
make infra-up
make infra-down

# 2. Database migrations (Flyway)
make migrate

# 3. Backend microservices only
make services-up
make services-down

# 4. Web frontend in Docker
make web-up
make web-down

# 5. Local development for Frontend (Vite HMR)
make web-dev
```

---

## Repository Structure

```text
superapp/
├── services/                     # Backend microservices
│   ├── api-gateway/              # Spring Cloud Gateway (8700)
│   ├── identity-service/         # Auth & OAuth2 provider
│   ├── user-service/             # User profile & avatar storage
│   ├── notification-service/     # Mailer & Kafka event consumers
│   ├── kyc-service/              # KYC workflow orchestrator
│   └── kyc-ml-service/           # FastAPI computer vision & ML service
├── web/                          # Web applications
│   └── kyc/                      # React/Vite KYC onboarding app
├── mobile/                       # Flutter cross-platform client
├── infra/                        # Infrastructure & orchestration
│   ├── compose/                  # Modular Docker Compose files
│   │   ├── docker-compose.infra.yml
│   │   ├── docker-compose.migrate.yml
│   │   ├── docker-compose.services.yml
│   │   ├── docker-compose.web.yml
│   │   └── docker-compose.yml
│   ├── docker/                   # Centralized Dockerfiles
│   │   ├── Dockerfile.jvm        # Unified Spring Boot JVM builder
│   │   ├── Dockerfile.flyway     # Unified Flyway migration runner
│   │   ├── Dockerfile.ml         # Python FastAPI ML runner
│   │   └── Dockerfile.web        # React Vite Nginx runner
│   └── scripts/                  # Init scripts (SQL, bash)
│       └── init-databases.sql
├── k8s/                          # Production Kubernetes manifests
├── Makefile                      # Monorepo automation commands
├── docker-compose.yml            # Root compose include entrypoint
└── .env.example                  # Environment configuration template
```
