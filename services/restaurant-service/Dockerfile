# =============================================================
# Restaurant Service — multi-stage build
# Depends on common-security + common-web.
# =============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY shared/common-security ./shared/common-security
COPY shared/common-web      ./shared/common-web
RUN mvn -B -f shared/common-security/pom.xml install -DskipTests \
 && mvn -B -f shared/common-web/pom.xml      install -DskipTests

COPY services/restaurant-service ./services/restaurant-service
RUN mvn -B -f services/restaurant-service/pom.xml clean package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl

WORKDIR /app
COPY --from=builder /build/services/restaurant-service/target/restaurant-service-*.jar /app/app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
