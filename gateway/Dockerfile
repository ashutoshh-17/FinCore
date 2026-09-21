# ── Stage 1: Build ────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Cache dependency downloads separately from source code
COPY pom.xml .
COPY bankflow-common/pom.xml bankflow-common/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY account-service/pom.xml account-service/pom.xml
COPY transaction-service/pom.xml transaction-service/pom.xml
COPY ledger-service/pom.xml ledger-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY fraud-service/pom.xml fraud-service/pom.xml
COPY ai-assistant-service/pom.xml ai-assistant-service/pom.xml

# Download dependencies (cached layer if POMs unchanged)
RUN mvn -pl bankflow-common,gateway -am dependency:go-offline -q

# Copy source and build
COPY bankflow-common/src bankflow-common/src
COPY gateway/src gateway/src
RUN mvn -pl bankflow-common,gateway -am package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="bankflow-team"

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app
COPY --from=builder /build/gateway/target/*.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
