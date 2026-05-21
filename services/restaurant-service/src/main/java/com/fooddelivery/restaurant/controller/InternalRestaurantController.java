package com.fooddelivery.restaurant.controller;

import com.fooddelivery.restaurant.dto.InternalRestaurantResponse;
import com.fooddelivery.restaurant.dto.ValidateOrderRequest;
import com.fooddelivery.restaurant.dto.ValidatedOrderResponse;
import com.fooddelivery.restaurant.service.RestaurantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Inter-service endpoints. Not routed by the API Gateway. Order Service is
 * the primary consumer.
 */
@RestController
@RequestMapping("/api/internal/restaurants")
public class InternalRestaurantController {

    private final RestaurantService service;

    public InternalRestaurantController(RestaurantService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InternalRestaurantResponse> getInternal(@PathVariable Long id) {
        return ResponseEntity.ok(service.getInternal(id));
    }

    @PostMapping("/{id}/validate-order")
    public ResponseEntity<ValidatedOrderResponse> validateOrder(
            @PathVariable Long id,
            @Valid @RequestBody ValidateOrderRequest request) {
        return ResponseEntity.ok(service.validateOrder(id, request));
    }
}
