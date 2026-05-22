# =============================================================
# Eureka Server — multi-stage build
# Build context is the repo root (set in docker-compose.yml).
# =============================================================

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Eureka depends on no shared libs — just copy and build.
COPY infrastructure/eureka-server ./infrastructure/eureka-server
RUN mvn -B -f infrastructure/eureka-server/pom.xml clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine

# curl is used by docker-compose healthchecks
RUN apk add --no-cache curl

WORKDIR /app
COPY --from=builder /build/infrastructure/eureka-server/target/eureka-server-*.jar /app/app.jar

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
