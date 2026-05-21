package com.fooddelivery.order.controller;

import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.order.service.OrderService;
import com.fooddelivery.shared.security.AuthenticatedUser;
import com.fooddelivery.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@CurrentUser AuthenticatedUser caller,
                                                    @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(caller, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getById(id));
    }

    @GetMapping("/my-orders")
    public ResponseEntity<List<OrderResponse>> myOrders(@CurrentUser AuthenticatedUser caller) {
        return ResponseEntity.ok(orderService.getMyOrders(caller));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<OrderResponse>> byRestaurant(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(orderService.getByRestaurant(restaurantId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long id,
                                                      @RequestParam String status) {
        return ResponseEntity.ok(orderService.updateStatus(id, status));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long id,
                                                @CurrentUser AuthenticatedUser caller) {
        return ResponseEntity.ok(orderService.cancel(id, caller));
    }
}
