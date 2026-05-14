package com.fooddelivery.order.controller;

import com.fooddelivery.order.dto.InternalOrderResponse;
import com.fooddelivery.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints reserved for inter-service calls. Delivery Service uses
 * {@code GET /api/internal/orders/{id}} to fetch the snapshot data it needs.
 */
@RestController
@RequestMapping("/api/internal/orders")
public class InternalOrderController {

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InternalOrderResponse> getInternal(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getInternal(id));
    }
}
