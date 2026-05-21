package com.fooddelivery.delivery;

import com.fooddelivery.shared.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Delivery Service — owns the delivery aggregate. Registers with Eureka
 * as {@code delivery-service}. Phase 7 talks to Order Service via Feign
 * to fetch order snapshot data at delivery-creation time. Phase 8 will
 * wire it up to RabbitMQ so deliveries are created event-driven.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableConfigurationProperties(JwtProperties.class)
public class DeliveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryServiceApplication.class, args);
    }
}
