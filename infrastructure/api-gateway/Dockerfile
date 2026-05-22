# =============================================================
# API Gateway — multi-stage build
# Reactive (WebFlux + Netty). Depends on common-security + common-web.
# =============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Install shared libs into the local Maven repository first.
COPY shared/common-security ./shared/common-security
COPY shared/common-web      ./shared/common-web
RUN mvn -B -f shared/common-security/pom.xml install -DskipTests \
 && mvn -B -f shared/common-web/pom.xml      install -DskipTests

# Build the gateway against the now-installed shared libs.
COPY infrastructure/api-gateway ./infrastructure/api-gateway
RUN mvn -B -f infrastructure/api-gateway/pom.xml clean package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl

WORKDIR /app
COPY --from=builder /build/infrastructure/api-gateway/target/api-gateway-*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
