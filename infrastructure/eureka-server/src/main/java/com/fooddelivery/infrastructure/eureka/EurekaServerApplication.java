package com.fooddelivery.infrastructure.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka service registry. Every business service registers here on startup;
 * the API Gateway resolves logical names ({@code lb://customer-service}) by
 * asking Eureka for live instances.
 *
 * <p>Dashboard: <a href="http://localhost:8761">http://localhost:8761</a>
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
