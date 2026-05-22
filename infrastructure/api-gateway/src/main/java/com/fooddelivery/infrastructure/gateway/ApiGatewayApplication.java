package com.fooddelivery.infrastructure.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway — the single public entry point.
 *
 * <p>Reactive Spring Boot app on {@code :8080}. Registers with Eureka so it
 * can both advertise itself and resolve {@code lb://service-name} routes to
 * live downstream instances.
 *
 * <p>Phase 9 sub-tasks fill in: routes (9.2), JWT validation (9.3),
 * rate limiting (9.4), circuit-breaker fallbacks (9.5).
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
