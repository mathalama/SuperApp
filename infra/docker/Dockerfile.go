FROM golang:alpine AS build
WORKDIR /app

RUN apk add --no-cache git ca-certificates

# Cache dependencies
COPY go.mod go.sum ./
RUN go mod download

# Build static binary
COPY . .
ARG CMD_PATH=./cmd/gateway
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o /app/server ${CMD_PATH}

# Production runtime image
FROM alpine:3.20
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN apk add --no-cache curl ca-certificates

WORKDIR /app
COPY --from=build /app/server /app/server
USER appuser

ARG PORT=8010
EXPOSE ${PORT}

ENTRYPOINT ["/app/server"]
