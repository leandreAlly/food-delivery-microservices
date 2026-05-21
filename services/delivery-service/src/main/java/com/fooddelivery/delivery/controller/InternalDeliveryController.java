package com.fooddelivery.delivery.controller;

import com.fooddelivery.delivery.dto.CreateDeliveryRequest;
import com.fooddelivery.delivery.dto.DeliveryResponse;
import com.fooddelivery.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Phase 7 bridge: {@code POST /api/internal/deliveries} lets you create
 * a delivery for an existing orderId manually, without RabbitMQ. Phase 8
 * keeps the underlying service method but routes calls through an event
 * consumer instead; this endpoint can stay (useful for replay / admin) or
 * be removed — decision deferred.
 */
@RestController
@RequestMapping("/api/internal/deliveries")
public class InternalDeliveryController {

    private final DeliveryService service;

    public InternalDeliveryController(DeliveryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DeliveryResponse> create(@Valid @RequestBody CreateDeliveryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createForOrder(request.orderId()));
    }
}
