# ── Stage 1: Build ────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

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

RUN mvn -pl bankflow-common,auth-service -am dependency:go-offline -q

COPY bankflow-common/src bankflow-common/src
COPY auth-service/src auth-service/src
RUN mvn -pl bankflow-common,auth-service -am package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="bankflow-team"

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app
COPY --from=builder /build/auth-service/target/*.jar app.jar

EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
