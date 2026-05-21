package com.fooddelivery.delivery.controller;

import com.fooddelivery.delivery.dto.DeliveryResponse;
import com.fooddelivery.delivery.service.DeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeliveryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<DeliveryResponse> getByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(service.getByOrderId(orderId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<DeliveryResponse>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(service.getByStatus(status));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<DeliveryResponse> updateStatus(@PathVariable Long id,
                                                         @RequestParam String status) {
        return ResponseEntity.ok(service.updateStatus(id, status));
    }
}
