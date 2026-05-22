# =============================================================
# Customer Service — multi-stage build
# Depends on common-security + common-web.
# =============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY shared/common-security ./shared/common-security
COPY shared/common-web      ./shared/common-web
RUN mvn -B -f shared/common-security/pom.xml install -DskipTests \
 && mvn -B -f shared/common-web/pom.xml      install -DskipTests

COPY services/customer-service ./services/customer-service
RUN mvn -B -f services/customer-service/pom.xml clean package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl

WORKDIR /app
COPY --from=builder /build/services/customer-service/target/customer-service-*.jar /app/app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
