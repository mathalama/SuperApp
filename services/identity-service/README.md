<div align="center">

# Identity Service

**Production-ready authentication & authorization microservice**

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![Gradle](https://img.shields.io/badge/Gradle-9+-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)

[![Spring Security](https://img.shields.io/badge/Spring_Security-6.x-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![JWT](https://img.shields.io/badge/JWT-HMAC--SHA256-000000?style=flat-square&logo=jsonwebtokens&logoColor=white)](https://jwt.io/)
[![OAuth2](https://img.shields.io/badge/OAuth2-Google_GitHub-EB5424?style=flat-square&logo=auth0&logoColor=white)](https://oauth.net/2/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-CC0200?style=flat-square&logo=flyway&logoColor=white)](https://flywaydb.org/)
[![Swagger](https://img.shields.io/badge/Swagger-OpenAPI_3.0-85EA2D?style=flat-square&logo=swagger&logoColor=black)](https://swagger.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

---

Issues **JWT access & refresh tokens**, serves as an **OAuth2 Authorization Server**, and supports **OAuth2 Login** with third-party providers (Google, GitHub) - all backed by **PostgreSQL** and **Redis**.

[Getting Started](#quick-start) · [API Reference](#api-endpoints) · [Architecture](#architecture) · [Deployment](#deployment)

</div>

---

## Table of Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Environment Variables](#environment-variables)
- [API Endpoints](#api-endpoints)
- [Usage Examples](#usage-examples)
- [JWT Token Structure](#jwt-token-structure)
- [Token Lifecycle](#token-lifecycle)
- [Email Verification Flow](#email-verification-flow)
- [Forgot Password Flow](#forgot-password-flow)
- [Input Validation](#input-validation)
- [Database Schema](#database-schema)
- [Project Structure](#project-structure-clean-architecture)
- [Security](#security)
- [Configuration](#configuration)
- [Development](#development)
- [Deployment](#deployment)
- [Monitoring & Health Checks](#monitoring--health-checks)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

---

## Features

| Feature | Description |
|:--------|:-----------|
| **JWT Access + Refresh Tokens** | Short-lived access token + long-lived refresh token with automatic rotation |
| **Token Blacklisting** | Instant access token revocation via Redis blacklist on logout |
| **OAuth2 Authorization Server** | Full OIDC support with RSA-256 signing |
| **OAuth2 Login** | Third-party authentication (Google, GitHub, and more) |
| **OAuth Provider Linking** | Link multiple OAuth providers to a single account for unified authentication |
| **User Management** | Registration, authentication, email / username login |
| **User Profile** | Get current user details and manage linked authentication methods |
| **Role-Based Access Control** | Multi-tier permissions — `USER`, `ADMIN`, `SUPER_ADMIN` |
| **Email Verification** | Token-based verification with Redis storage (30 min TTL) |
| **Resend Cooldown** | Rate-limited email resend (60 sec throttle) |
| **Forgot Password** | Email-based password reset with time-limited Redis token |
| **Logout & Revocation** | `POST /auth/logout` blacklists access token and revokes refresh token |
| **Input Validation** | Jakarta Bean Validation on all DTOs (`@NotBlank`, `@Email`, `@Size`, `@Pattern`) |
| **Clean Architecture** | Domain → Application → Infrastructure → Presentation |
| **Async Email** | Non-blocking sending via Spring `TaskExecutor` |
| **Automatic Migrations** | Flyway-managed schema versioning |
| **API Documentation** | Swagger UI with OpenAPI 3.0 |
| **CORS Support** | Configurable cross-origin access |
| **Service-to-Service Auth** | `POST /auth/validate` for inter-microservice token validation |

---

## Technology Stack

| Layer | Technology | Version |
|:------|:-----------|:--------|
| **Language** | Java (OpenJDK) | 21 |
| **Framework** | Spring Boot | 4.0.3 |
| **Security** | Spring Security + OAuth2 Authorization Server + OAuth2 Client | 6.x |
| **ORM** | Spring Data JPA + Hibernate | — |
| **Database** | PostgreSQL | 15+ |
| **Migrations** | Flyway | — |
| **Cache / Tokens** | Redis (Lettuce driver) | 7+ |
| **JWT** | jjwt (io.jsonwebtoken) | 0.12.3 |
| **API Docs** | SpringDoc OpenAPI | 3.0.0 |
| **Build** | Gradle | 9+ |
| **Container** | Docker & Docker Compose | — |

---

## Architecture

```
┌───────────────┐      ┌──────────────────────────────────────────────┐
│   Frontend    │─────▶│         Identity Service (Spring Boot)       │
│ (React, etc.) │      │                                              │
└───────────────┘      │  ① OAuth2 Authorization Server              │
                       │     /oauth2/**  /.well-known/**              │
┌───────────────┐      │     Issues OAuth2/OIDC tokens (RSA-256)     │
│ Microservices │─────▶│                                              │
│   (Backend)   │      │  ② JWT API — Stateless                      │
└───────────────┘      │     /auth/**  /api/**                        │
                       │     Access + Refresh tokens (HMAC-SHA256)    │
┌───────────────┐      │     Redis token blacklist & refresh store    │
│ Google OAuth2 │◀────▶│                                              │
│ GitHub OAuth2 │◀────▶│  ③ OAuth2 Login + Form Login                │
└───────────────┘      │     Session-based fallback auth              │
                       └──────────────┬───────────────────────────────┘
                                      │
                          ┌───────────┴───────────┐
                          │                       │
                    ┌─────┴─────┐          ┌──────┴──────┐
                    │PostgreSQL │          │    Redis     │
                    │   15      │          │      7       │
                    │Users,Roles│          │Refresh tokens│
                    └───────────┘          │Access blackl.│
                                           │Verify tokens│
                                           └─────────────┘
```

The service exposes **three security filter chains** ordered by priority:

1. **API chain** (`/auth/**`, `/api/**`, `/actuator/**`) — stateless JWT, no sessions
2. **Default chain** (everything else) — OAuth2 Login + Form Login with sessions
3. **Authorization Server** — built-in Spring Authorization Server endpoints

---

## Prerequisites

| Requirement | Minimum |
|:------------|:--------|
| JDK | 21+ |
| Docker & Docker Compose | latest |
| Gradle | 9+ _or_ use bundled `./gradlew` |
| PostgreSQL | 15+ _(or via Docker)_ |
| Redis | 7+ _(or via Docker)_ |

---

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/yourusername/identity-service.git
cd identity-service
```

### 2. Create a `.env` file

```bash
cat > .env << 'EOF'
POSTGRES_DB=identity_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=secure_password

JWT_SECRET=generate-with-openssl-rand-base64-32
JWT_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000

FRONTEND_URL=http://localhost:3000
BASE_URL=http://localhost:8080

REDIS_HOST=redis
REDIS_PORT=6379

SECURITY_USER=admin
SECURITY_PASSWORD=admin

GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
EOF
```

### 3. Start infrastructure only

```bash
docker compose up -d postgres redis
```

### 4. Run the application

```bash
./gradlew bootRun
```

### Or launch everything in Docker

```bash
docker compose up -d --build
```

> The service will be available at **http://localhost:8080**

---

## Environment Variables

| Variable | Required | Default | Description |
|:---------|:--------:|:-------:|:------------|
| `POSTGRES_DB` | ✅ | — | PostgreSQL database name |
| `POSTGRES_USER` | ✅ | — | PostgreSQL username |
| `POSTGRES_PASSWORD` | ✅ | — | PostgreSQL password |
| `JWT_SECRET` | ✅ | — | HMAC key for JWT signing (min 32 chars) |
| `JWT_EXPIRATION` | ✅ | — | Access token lifetime in **ms** (e.g. `900000` = 15 min) |
| `JWT_REFRESH_EXPIRATION` | — | `604800000` | Refresh token lifetime in **ms** (default 7 days) |
| `FRONTEND_URL` | ✅ | — | CORS allowed origin |
| `BASE_URL` | ✅ | — | OAuth2 AS issuer URL |
| `REDIS_HOST` | — | `localhost` | Redis hostname |
| `REDIS_PORT` | — | `6379` | Redis port |
| `REDIS_PASSWORD` | — | _(empty)_ | Redis password |
| `SECURITY_USER` | — | — | Spring Security default user |
| `SECURITY_PASSWORD` | — | — | Spring Security default password |
| `GOOGLE_CLIENT_ID` | — | — | Google OAuth2 Client ID |
| `GOOGLE_CLIENT_SECRET` | — | — | Google OAuth2 Client Secret |
| `GITHUB_CLIENT_ID` | — | — | GitHub OAuth2 Client ID |
| `GITHUB_CLIENT_SECRET` | — | — | GitHub OAuth2 Client Secret |

---

## API Endpoints

### Authentication (Public)

| Method | Endpoint | Description | Body |
|:------:|:---------|:------------|:-----|
| `POST` | `/auth/register` | Register new user | `{ username, email, password }` |
| `POST` | `/auth/authenticate` | Login → access + refresh tokens | `{ login, password }` |
| `POST` | `/auth/refresh` | Refresh token pair (rotation) | `{ refreshToken }` |
| `POST` | `/auth/verify-email` | Verify email token | `{ token }` |
| `POST` | `/auth/resend-verification` | Resend verification | `{ email }` |
| `POST` | `/auth/forgot-password` | Request password reset email | `{ email }` |
| `POST` | `/auth/reset-forgotten-password` | Set new password via reset token | `{ token, newPassword }` |

### User Profile (Protected)

| Method | Endpoint | Description |
|:------:|:---------|:------------|
| `GET` | `/auth/me` | Get current user profile |
| `GET` | `/auth/me/providers` | List linked OAuth providers |
| `DELETE` | `/auth/me/providers/{provider}` | Unlink OAuth provider |

### Account Management (Protected)

| Method | Endpoint | Description |
|:------:|:---------|:------------|
| `POST` | `/auth/logout` | Revoke refresh token + blacklist access token |
| `POST` | `/auth/reset-password` | Change password |

### Service-to-Service (Internal)

| Method | Endpoint | Description |
|:------:|:---------|:------------|
| `POST` | `/auth/validate` | Validate JWT token and get user info |

### OAuth2 Authorization Server

| Method | Endpoint | Description |
|:------:|:---------|:------------|
| `GET` | `/.well-known/openid-configuration` | OIDC discovery |
| `POST` | `/oauth2/token` | Token endpoint |
| `GET` | `/oauth2/authorize` | Authorization endpoint |
| `GET` | `/oauth2/jwks` | JSON Web Key Set |

### OAuth2 Social Login

| Method | Endpoint | Description |
|:------:|:---------|:------------|
| `GET` | `/oauth2/authorization/google` | Redirect to Google |
| `GET` | `/oauth2/authorization/github` | Redirect to GitHub |

### Docs & Health

| Method | Endpoint | Description |
|:------:|:---------|:------------|
| `GET` | `/swagger-ui/index.html` | Swagger UI |
| `GET` | `/v3/api-docs` | OpenAPI 3.0 spec (JSON) |
| `GET` | `/actuator/health` | Health check |

---

## Usage Examples

<details>
<summary><b>Register a new user</b></summary>

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "email": "john@example.com",
    "password": "SecurePassword123!"
  }'
```

```json
// 201 Created
{
  "message": "User registered successfully. Please check your email to verify your account."
}
```

</details>

<details>
<summary><b>Authenticate & get tokens</b></summary>

```bash
curl -X POST http://localhost:8080/auth/authenticate \
  -H "Content-Type: application/json" \
  -d '{
    "login": "john_doe",
    "password": "SecurePassword123!"
  }'
```

```json
// 200 OK
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoiYWNjZXNzIiwic3ViIjoiNTUw...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInN1YiI6IjU1MC..."
}
```

</details>

<details>
<summary><b>Refresh tokens</b></summary>

```bash
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoicmVmcmVzaCIs..."
  }'
```

```json
// 200 OK — old refresh token is revoked, new pair issued
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoiYWNjZXNzIiw...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoicmVmcmVzaCIs..."
}
```

</details>

<details>
<summary><b>Logout (revoke all tokens)</b></summary>

```bash
curl -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

```
// 204 No Content
// Access token is blacklisted in Redis (TTL = remaining lifetime)
// Refresh token is deleted from Redis
```

</details>

<details>
<summary><b>Get current user profile</b></summary>

```bash
curl http://localhost:8080/auth/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

```json
// 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "email": "john@example.com",
  "emailVerified": true,
  "accountState": "ACTIVE",
  "roles": ["ROLE_USER"],
  "createdAt": 1740240000000
}
```

</details>

<details>
<summary><b>List linked OAuth providers</b></summary>

```bash
curl http://localhost:8080/auth/me/providers \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

```json
// 200 OK
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "providerName": "GOOGLE",
    "providerEmail": "john@gmail.com",
    "linkedAt": 1740239000000,
    "lastLoginAt": 1740240000000
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440002",
    "providerName": "GITHUB",
    "providerEmail": "john_dev",
    "linkedAt": 1740238000000,
    "lastLoginAt": 1740235000000
  }
]
```

</details>

<details>
<summary><b>Unlink OAuth provider</b></summary>

```bash
curl -X DELETE http://localhost:8080/auth/me/providers/GOOGLE \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

```
// 204 No Content
// Provider unlinked successfully
```

</details>

<details>
<summary><b>Validate token (service-to-service)</b></summary>

```bash
curl -X POST http://localhost:8080/auth/validate \
  -H "Content-Type: application/json" \
  -d '{
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoiYWNjZXNzIiw..."
  }'
```

```json
// 200 OK (on success - check valid flag)
{
  "valid": true,
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "email": "john@example.com",
  "roles": ["ROLE_USER"],
  "message": "Token is valid"
}
```

```json
// 200 OK (on failure - check valid flag)
{
  "valid": false,
  "userId": null,
  "username": null,
  "email": null,
  "roles": [],
  "message": "Invalid or expired token"
}
```

</details>

<details>
<summary><b>Verify email</b></summary>

```bash
curl -X POST http://localhost:8080/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{
    "token": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

```json
// 200 OK
{
  "message": "Email verified successfully",
  "verified": true
}
```

</details>

<details>
<summary><b>Resend verification email</b></summary>

```bash
curl -X POST http://localhost:8080/auth/resend-verification \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com"
  }'
```

```json
// 200 OK
{
  "message": "Verification email sent successfully",
  "verified": false
}
```

</details>

<details>
<summary><b>Forgot password</b></summary>

```bash
curl -X POST http://localhost:8080/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com"
  }'
```

```json
// 200 OK
{
  "message": "If the email exists, a password reset link has been sent."
}
```

</details>

<details>
<summary><b>Reset forgotten password</b></summary>

```bash
curl -X POST http://localhost:8080/auth/reset-forgotten-password \
  -H "Content-Type: application/json" \
  -d '{
    "token": "550e8400-e29b-41d4-a716-446655440000",
    "newPassword": "NewSecurePassword456!"
  }'
```

```json
// 200 OK
{
  "message": "Password has been reset successfully. Please log in with your new password."
}
```

</details>

<details>
<summary><b>Call a protected endpoint</b></summary>

```bash
curl http://localhost:8080/api/some-endpoint \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

</details>

---

## JWT Token Structure

### Access Token

```json
{
  "type": "access",
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "roles": ["ROLE_USER"],
  "iss": "Identity Service",
  "iat": 1740240000,
  "exp": 1740240900,
  "jti": "unique-token-identifier"
}
```

### Refresh Token

```json
{
  "type": "refresh",
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "iss": "Identity Service",
  "iat": 1740240000,
  "exp": 1740844800,
  "jti": "unique-refresh-token-id"
}
```

| Claim | Description |
|:------|:-----------|
| `type` | Token type: `access` or `refresh` |
| `sub` | User UUID |
| `roles` | Granted authorities (access token only) |
| `iss` | Issuer identifier |
| `iat` | Issued-at (Unix epoch) |
| `exp` | Expiration (Unix epoch) |
| `jti` | Unique token ID (used for blacklisting & refresh validation) |

---

## Token Lifecycle

```
 ┌──────────┐          ┌──────────────┐          ┌───────┐
 │  Client  │          │Identity Svc  │          │ Redis │
 └────┬─────┘          └──────┬───────┘          └───┬───┘
      │                       │                      │
      │  POST /authenticate   │                      │
      │──────────────────────▶│                      │
      │                       │  Generate access     │
      │                       │  Generate refresh    │
      │                       │── store jti ────────▶│ SET refresh:token:{userId} (7d)
      │◁── { accessToken,     │                      │
      │      refreshToken } ──│                      │
      │                       │                      │
      │  GET /api/** (Bearer) │                      │
      │──────────────────────▶│  validate signature  │
      │                       │  check type=access   │
      │                       │── check blacklist ──▶│ EXISTS blacklist:access:{jti}
      │                       │◁── false ───────────│
      │◁── 200 OK ───────────│                      │
      │                       │                      │
      ┆  (access token expired)                      │
      │                       │                      │
      │  POST /auth/refresh   │                      │
      │──────────────────────▶│  parse refresh token │
      │                       │── validate jti ────▶│ GET refresh:token:{userId}
      │                       │◁── matches ────────│
      │                       │── DEL old refresh ─▶│
      │                       │  Generate new pair   │
      │                       │── store new jti ───▶│ SET refresh:token:{userId} (7d)
      │◁── { accessToken,     │                      │
      │      refreshToken } ──│                      │
      │                       │                      │
      │  POST /auth/logout    │                      │
      │  (Bearer: access)     │                      │
      │──────────────────────▶│── blacklist access ─▶│ SET blacklist:access:{jti} (remaining TTL)
      │                       │── DEL refresh ─────▶│ DEL refresh:token:{userId}
      │◁── 204 No Content ───│                      │
```

### Redis Key Schema

| Key Pattern | Value | TTL | Purpose |
|:------------|:------|:----|:--------|
| `refresh:token:{userId}` | Refresh token `jti` | 7 days | Refresh token validation & rotation |
| `blacklist:access:{jti}` | `"revoked"` | Remaining access token lifetime | Instant access token revocation |
| `verify:token:{hash}` | User UUID | 30 min | Email verification |
| `verify:cooldown:{userId}` | Timestamp | 60 sec | Resend rate limiting |
| `reset:token:{hash}` | User UUID | 30 min | Password reset token |
| `reset:cooldown:{userId}` | Timestamp | 60 sec | Password reset rate limiting |

---

## Email Verification Flow

```
 ┌──────────┐          ┌──────────────┐          ┌───────┐
 │  Client  │          │Identity Svc  │          │ Redis │
 └────┬─────┘          └──────┬───────┘          └───┬───┘
      │  POST /auth/register  │                      │
      │──────────────────────▶│  Create user          │
      │                       │  (PENDING_VERIFICATION)│
      │                       │  Generate UUID token   │
      │                       │──── SHA-256 hash ────▶│ SETEX (30 min)
      │                       │  Send email (async)    │
      │◁─── 201 Created ─────│                        │
      │                       │                        │
      │  POST /auth/verify    │                        │
      │──────────────────────▶│  Hash incoming token   │
      │                       │──── GET ─────────────▶│
      │                       │◁─── user_id ──────────│
      │                       │  Set ACTIVE            │
      │                       │──── DEL ─────────────▶│
      │◁─── 200 Verified ────│                        │
      │                       │                        │
      │  POST /auth/resend    │                        │
      │──────────────────────▶│  Check cooldown (60s)  │
      │                       │──── GET cooldown ────▶│
      │                       │  Generate new token    │
      │                       │──── SETEX ───────────▶│
      │                       │  Send email (async)    │
      │◁─── 200 Sent ────────│                        │
```

---

## Forgot Password Flow

```
 ┌──────────┐          ┌──────────────┐          ┌───────┐
 │  Client  │          │Identity Svc  │          │ Redis │
 └────┬─────┘          └──────┬───────┘          └───┬───┘
      │  POST /auth/          │                      │
      │  forgot-password      │                      │
      │──────────────────────▶│  Lookup user by email │
      │                       │  Check cooldown (60s) │
      │                       │──── GET cooldown ───▶│
      │                       │  Generate UUID token  │
      │                       │──── SHA-256 hash ───▶│ SETEX (30 min)
      │                       │  Send email (async)   │
      │◁─── 200 OK ──────────│                       │
      │                       │                       │
      │  POST /auth/          │                       │
      │  reset-forgotten-pwd  │                       │
      │──────────────────────▶│  Hash incoming token  │
      │                       │──── GET ────────────▶│
      │                       │◁─── user_id ─────────│
      │                       │  Set new password     │
      │                       │  (BCrypt)             │
      │                       │──── DEL token ──────▶│
      │                       │  Revoke refresh token │
      │                       │──── DEL refresh ────▶│
      │◁─── 200 OK ──────────│                       │
```

---

## Input Validation

All request DTOs are validated with **Jakarta Bean Validation** (`spring-boot-starter-validation`). Requests failing validation receive a `400 Bad Request` response.

| DTO | Field | Constraints |
|:----|:------|:------------|
| `SignUpRegister` | `username` | `@NotBlank`, `@Size(3..50)`, `@Pattern(^[a-zA-Z0-9_]+$)` |
| | `email` | `@NotBlank`, `@Email` |
| | `password` | `@NotBlank`, `@Size(8..128)` |
| `SignInRequest` | `login` | `@NotBlank` |
| | `password` | `@NotBlank` |
| `RefreshTokenRequest` | `refreshToken` | `@NotBlank` |
| `ResetPasswordRequest` | `username` | `@NotBlank` |
| | `oldPassword` | `@NotBlank` |
| | `newPassword` | `@NotBlank`, `@Size(8..128)` |
| `ForgotPasswordRequest` | `email` | `@NotBlank`, `@Email` |
| `NewPasswordRequest` | `token` | `@NotBlank` |
| | `newPassword` | `@NotBlank`, `@Size(8..128)` |
| `VerifyEmailRequest` | `token` | `@NotBlank` |
| `ResendVerificationRequest` | `email` | `@NotBlank`, `@Email` |
| `UpdateRequest` | `username` | `@NotBlank`, `@Size(3..50)` |
| | `email` | `@NotBlank`, `@Email` |
| | `password` | `@NotBlank`, `@Size(8..128)` |

---

## Database Schema

### `users`

```sql
CREATE TABLE users (
    id                        UUID PRIMARY KEY,
    username                  VARCHAR(255) NOT NULL UNIQUE,
    email                     VARCHAR(255) NOT NULL UNIQUE,
    password                  VARCHAR(255) NOT NULL,
    account_state             VARCHAR(50),
    security_status           VARCHAR(50),
    email_verified            BOOLEAN DEFAULT false,
    verified_at               TIMESTAMP,
    last_verification_sent_at TIMESTAMP,
    created_at                TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_account_state  ON users(account_state);
CREATE INDEX idx_users_email          ON users(email);
CREATE INDEX idx_users_username       ON users(username);
CREATE INDEX idx_users_email_verified ON users(email_verified);
```

### `roles`

```sql
CREATE TABLE roles (
    id   UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Seed data
INSERT INTO roles (name) VALUES ('ROLE_USER'), ('ROLE_ADMIN'), ('ROLE_SUPER_ADMIN');
```

### `users_roles` (junction)

```sql
CREATE TABLE users_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
```

### `oauth_providers` (Multi-provider authentication)

```sql
CREATE TABLE oauth_providers (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL,
    provider_name VARCHAR(50) NOT NULL,
    provider_id   VARCHAR(500) NOT NULL,
    provider_email VARCHAR(255),
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW(),
    last_login_at TIMESTAMP,
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_provider_per_user UNIQUE (user_id, provider_name)
);

CREATE INDEX idx_oauth_provider_name ON oauth_providers(provider_name);
CREATE INDEX idx_oauth_provider_id ON oauth_providers(provider_id);
CREATE INDEX idx_oauth_user_id ON oauth_providers(user_id);
```

### ER Diagram

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│    users     │       │ users_roles  │       │    roles     │
├──────────────┤       ├──────────────┤       ├──────────────┤
│ id       (PK)│──┐    │ user_id (FK) │    ┌──│ id       (PK)│
│ username     │  └───▶│ role_id (FK) │◀───┘  │ name         │
│ email        │       └──────────────┘       └──────────────┘
│ password     │
│ account_state│  Enums: PENDING_VERIFICATION · ACTIVE · DISABLED · DELETED
│ security_stat│  Enums: MFA_REQUIRED │ MFA_VERIFIED │ PENDING │ VERIFIED
│ email_verified│
│ created_at   │
└──────────────┘
       │
       │ 1:N
       │
┌──────┴──────────────┐
│ oauth_providers     │
├─────────────────────┤
│ id            (PK)  │
│ provider_name       │ (GOOGLE, GITHUB, etc)
│ provider_id         │ (Provider's unique ID)
│ provider_email      │ (Email from provider)
│ last_login_at       │ (Login tracking)
└─────────────────────┘
```

---

## Project Structure (Clean Architecture)

```
src/main/java/dev/mathalama/identityservice/
│
├── domain/                              ← Business logic & rules
│   ├── entity/
│   │   ├── Users.java                     UserDetails aggregate root
│   │   ├── Role.java                      GrantedAuthority entity
│   │   └── OAuthProvider.java             OAuth provider link tracking
│   ├── enums/
│   │   ├── AccountState.java              PENDING_VERIFICATION · ACTIVE · DISABLED · DELETED
│   │   └── SecurityStatus.java            MFA_REQUIRED · MFA_VERIFIED · PENDING · VERIFIED
│   ├── repository/
│   │   └── OAuthProviderRepository.java    OAuth provider queries
│   └── exception/
│       ├── UnauthorizedException.java
│       ├── UserAlreadyExistException.java
│       └── UserNotFoundException.java
│
├── application/                         ← Use cases & DTOs
│   ├── dto/
│   │   ├── auth/
│   │   │   ├── AuthResponse.java           Access + refresh token pair
│   │   │   ├── CurrentUserDto.java         Current user profile
│   │   │   ├── OAuthProviderDto.java       OAuth provider link info
│   │   │   ├── RefreshTokenRequest.java    Refresh token request
│   │   │   ├── ForgotPasswordRequest.java  Forgot password request
│   │   │   ├── NewPasswordRequest.java     Password reset via token
│   │   │   ├── SignUpRegister.java         Registration request
│   │   │   ├── SignInRequest.java          Login request
│   │   │   ├── TokenValidationRequest.java Service-to-service token validation
│   │   │   ├── TokenValidationResponse.java Token validation result
│   │   │   ├── ResetPasswordRequest.java   Password change (authenticated)
│   │   │   ├── VerifyEmailRequest.java     Email verification request
│   │   │   ├── ResendVerificationRequest.java Resend request
│   │   │   └── VerificationResponse.java   Verification status
│   │   └── user/
│   │       └── UpdateRequest.java          User profile update
│   └── service/
│       ├── AuthService.java                Interface — auth use cases
│       ├── EmailService.java               Interface — email sending
│       ├── JwtService.java                 Interface — JWT & token ops
│       ├── VerificationTokenService.java   Interface — verification token mgmt
│       ├── OAuthProviderService.java       Interface — OAuth provider management
│       └── impl/
│           └── AuthServiceImpl.java        Core auth logic
│
├── infrastructure/                      ← Frameworks & drivers
│   ├── repository/
│   │   ├── UserRepository.java             Spring Data JPA
│   │   ├── RoleRepository.java             Spring Data JPA
│   │   └── OAuthProviderRepository.java    Spring Data JPA (OAuth queries)
│   ├── service/
│   │   ├── EmailServiceImpl.java           Async email via TaskExecutor
│   │   ├── JwtServiceImpl.java             JWT gen/validation, refresh store, blacklist
│   │   └── VerificationTokenRedisService  Redis verification token storage
│   └── config/
│       ├── SecurityConfig.java             3 ordered filter chains
│       ├── JwtAuthenticationFilter.java    Bearer → SecurityContext (+ blacklist check)
│       ├── AsyncConfig.java                Thread pool config
│       ├── RedisConfig.java                Redis connection & serialization
│       ├── PasswordConfig.java             BCryptPasswordEncoder
│       ├── WebConfig.java                  CORS configuration
│       └── FrontendProperties.java         @ConfigurationProperties
│
├── presentation/                        ← HTTP entry points
│   └── controller/
│       └── AuthController.java             /auth/** REST controller (15 endpoints)
│
└── IdentityServiceApplication.java      ← Spring Boot main class
```

---

## Security

### Password Handling

- **BCrypt** hashing with per-user salt
- Passwords are never logged or returned in responses

### JWT Token Security

- Signed with **HMAC-SHA256** (`JWT_SECRET`)
- **Access token**: short-lived (default 15 min), carries user roles
- **Refresh token**: long-lived (default 7 days), stored in Redis by `jti`
- **Refresh token rotation**: each use invalidates the old token and issues a new pair
- **Access token blacklisting**: on logout, access token `jti` is added to Redis with TTL = remaining lifetime
- Payload: user UUID + roles — **no** sensitive data
- Validated on every request by `JwtAuthenticationFilter`

### Token Revocation Strategy

| Scenario | What Happens |
|:---------|:-------------|
| **Logout** | Access token blacklisted + refresh token deleted from Redis |
| **Refresh** | Old refresh token deleted, new pair issued (rotation) |
| **Reuse attack** | If an already-used refresh token is presented, all tokens for that user are revoked |
| **Access token expires** | Naturally removed — no Redis cleanup needed |
| **Blacklist entry expires** | Redis auto-deletes when access token would have expired anyway |

### Email Verification Tokens

- Stored in **Redis** (not the database)
- **SHA-256** hashed before storage — raw token only exists in the email link
- **30-minute** TTL, one-time use, deleted after verification
- Resend rate-limited to **60 seconds**
### Password Reset Tokens

- Same Redis-based flow as email verification
- **SHA-256** hashed, **30-minute** TTL, one-time use
- On successful reset all refresh tokens are revoked (forces re-login)
- Rate-limited to **60 seconds** between requests

### Account State Enforcement

| `AccountState` | `isEnabled()` | `isAccountNonLocked()` | `isAccountNonExpired()` |
|:---------------|:-:|:-:|:-:|
| `PENDING_VERIFICATION` | ✅ | ✅ | ✅ |
| `ACTIVE` | ✅ | ✅ | ✅ |
| `DISABLED` | ✅ | ❌ | ✅ |
| `DELETED` | ❌ | ✅ | ❌ |

Spring Security checks these methods during authentication — disabled and deleted accounts cannot log in.
### Authentication Pipeline

```
HTTP Request
  └─▶ JwtAuthenticationFilter
        ├── Extract Bearer token from Authorization header
        ├── Validate HMAC-SHA256 signature
        ├── Assert token not expired
        ├── Assert type = "access"
        ├── Check Redis blacklist (blacklist:access:{jti})
        ├── Parse user UUID + roles
        ├── Load User entity from DB
        └── Populate SecurityContext
              └─▶ @PreAuthorize / @Secured annotations enforce role checks
```

---

## Configuration

### Application Properties

Key configuration in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/${POSTGRES_DB}
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
  flyway:
    enabled: true
    locations: classpath:db/migration

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION}
  refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000}

app:
  frontend:
    url: ${FRONTEND_URL:http://localhost:3000}
  verification:
    token-expiry-minutes: 30
    resend-cooldown-seconds: 60
```

### Profiles

| Profile | Use |
|:--------|:----|
| _default_ | Local development (`localhost` DB & Redis) |
| _docker_ | Docker Compose network (`postgres`, `redis` hostnames) |

### Logging

```yaml
logging:
  level:
    dev.mathalama.identityservice: DEBUG
    org.springframework.security: INFO
```

---

## Development

```bash
# Build
./gradlew clean build

# Run tests
./gradlew test

# Start locally (requires running Postgres + Redis)
./gradlew bootRun

# Build Docker image
docker build -t mathalama/identity-service:latest .
```

---

## Deployment

### Docker Compose (recommended for dev / staging)

```bash
docker compose up -d --build
```

This spins up three containers:

| Container | Image | Port |
|:----------|:------|:-----|
| `identity-service` | Custom build | `8080` |
| `identity-postgres` | `postgres:15-alpine` | `5432` |
| `identity-redis` | `redis:7-alpine` | `6379` |

### Production Checklist

```bash
# Generate a strong JWT secret
openssl rand -base64 32

# Required env vars
JWT_SECRET=<generated_above>
JWT_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000
FRONTEND_URL=https://yourdomain.com
BASE_URL=https://api.yourdomain.com
POSTGRES_PASSWORD=<strong_password>
REDIS_PASSWORD=<strong_password>
```

- [ ] Use external managed PostgreSQL & Redis
- [ ] Enable TLS termination (reverse proxy / load balancer)
- [ ] Rotate `JWT_SECRET` periodically
- [ ] Set `spring.jpa.show-sql=false`
- [ ] Enable Prometheus metrics scraping (`/actuator/prometheus`)
- [ ] Set short access token TTL (`JWT_EXPIRATION=900000` = 15 min)
- [ ] Monitor Redis memory usage (blacklist entries auto-expire)

---

## Monitoring & Health Checks

```bash
curl http://localhost:8080/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "db":    { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

| Endpoint | Purpose |
|:---------|:--------|
| `/actuator/health` | Liveness & readiness |
| `/actuator/prometheus` | Prometheus metrics |

---

## Troubleshooting

| Problem | Solution |
|:--------|:--------|
| _JWT claims string is empty_ | Ensure header is `Authorization: Bearer <token>` (note the space) |
| _Token is not an access token_ | You're sending a refresh token to an API endpoint — use the access token |
| _Access token has been revoked_ | User has logged out — re-authenticate or use refresh token first |
| _Refresh token has been revoked or is invalid_ | Refresh token was already used (rotation) or user logged out — re-authenticate |
| _User not found_ | Verify user exists and email is verified (`account_state = ACTIVE`) |
| _Verification email was recently sent_ | Wait 60 seconds before resending |
| _Invalid token_ | Token expired (30 min window) or already used |
| _Account is disabled/deleted_ | User account is `DISABLED` or `DELETED` — contact support |
| _Forgot password email not received_ | Verify email exists in system; check spam folder; wait 60s before re-requesting |
| _Validation error (400)_ | Check request body matches DTO constraints (see [Input Validation](#input-validation)) |
| _Redis connection failed_ | Check `REDIS_HOST` / `REDIS_PORT` and that Redis is running |

---

## Contributing

1. **Fork** the repository
2. Create a feature branch — `git checkout -b feature/amazing-feature`
3. Follow **Clean Architecture** layer boundaries
4. Add tests for new functionality
5. Commit — `git commit -m 'Add amazing feature'`
6. Push — `git push origin feature/amazing-feature`
7. Open a **Pull Request**

---

## License

Distributed under the **MIT License**. See [LICENSE](LICENSE) for details.

---

<div align="center">

**Built with Spring Boot 4 & Java 21**

</div>
