# ─── Build stage ─────────────────────────────────────
# Uses the official Gradle image — no need for a local gradlew
FROM gradle:8.13-jdk21-alpine AS builder

WORKDIR /app

# Cache Gradle dependencies first (layer reuse)
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN gradle dependencies --no-daemon || true

# Copy source and build
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ─── Production stage ────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS production

WORKDIR /app

RUN addgroup -S valoracloud && adduser -S valoracloud -G valoracloud

COPY --from=builder /app/build/libs/*.jar app.jar

# Flyway migrations run automatically on Spring Boot startup

EXPOSE 8080

USER valoracloud

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
