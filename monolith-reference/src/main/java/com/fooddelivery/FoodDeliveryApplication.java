package com.fooddelivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Food Delivery Platform Monolith — Entry Point
 *
 * This is a MONOLITHIC application containing ALL domains:
 *  - Customer Management  (registration, auth, profiles, addresses)
 *  - Restaurant Management (restaurants, menus, availability)
 *  - Order Management     (cart, placement, order lifecycle)
 *  - Delivery Management  (assignment, tracking, status updates)
 *
 * YOUR TASK: Refactor this into 4 independent microservices.
 *
 * Key problems with this monolith:
 *  1. All entities share ONE database with cross-domain foreign keys
 *  2. Services call each other directly via in-process method calls
 *  3. Delivery assignments happen SYNCHRONOUSLY during order placement
 *  4. A single JAR — you cannot scale order processing independently
 *  5. A bug in Delivery logic crashes the entire application
 */
@SpringBootApplication
public class FoodDeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodDeliveryApplication.class, args);
    }
}
