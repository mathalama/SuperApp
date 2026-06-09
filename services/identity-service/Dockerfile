# syntax=docker/dockerfile:1
# Build Stage
FROM gradle:8.14 AS builder
WORKDIR /app

# Копируем всё
COPY . .

# Собираем с использованием BuildKit Cache (это то, что используют в проде!)
# Docker смонтирует папку кеша Gradle, которая не будет удаляться между билдами
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    --mount=type=cache,target=/home/gradle/.gradle/wrapper \
    gradle clean build -x test --no-daemon
# Run Stage
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8010
ENTRYPOINT ["java", "-jar", "app.jar"]