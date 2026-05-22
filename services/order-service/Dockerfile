# =============================================================
# Order Service — multi-stage build
# Depends on common-security + common-web + common-events.
# =============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY shared/common-security ./shared/common-security
COPY shared/common-web      ./shared/common-web
COPY shared/common-events   ./shared/common-events
RUN mvn -B -f shared/common-security/pom.xml install -DskipTests \
 && mvn -B -f shared/common-web/pom.xml      install -DskipTests \
 && mvn -B -f shared/common-events/pom.xml   install -DskipTests

COPY services/order-service ./services/order-service
RUN mvn -B -f services/order-service/pom.xml clean package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl

WORKDIR /app
COPY --from=builder /build/services/order-service/target/order-service-*.jar /app/app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
