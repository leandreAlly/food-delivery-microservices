package com.fooddelivery.order;

import com.fooddelivery.shared.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Order Service — owns the order aggregate (Order + OrderItem). Registers with
 * Eureka as {@code order-service}. Calls Customer Service and Restaurant Service
 * via Feign at order-placement time and snapshots the data so reads stay local.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableConfigurationProperties(JwtProperties.class)
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
