package com.fooddelivery.delivery.service;

import com.fooddelivery.delivery.client.OrderServiceClient;
import com.fooddelivery.delivery.client.dto.OrderSummary;
import com.fooddelivery.delivery.dto.DeliveryResponse;
import com.fooddelivery.delivery.model.Delivery;
import com.fooddelivery.delivery.repository.DeliveryRepository;
import com.fooddelivery.shared.web.exception.DuplicateResourceException;
import com.fooddelivery.shared.web.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    // Simulated driver pool — same as the monolith. A real platform would
    // split this into its own Driver Service. Out of scope for Module 9.
    private static final String[] DRIVERS = {
            "Carlos Martinez", "Sarah Johnson", "Mike Chen", "Priya Patel", "James Wilson"
    };
    private static final String[] PHONES = {
            "+1-555-0101", "+1-555-0102", "+1-555-0103", "+1-555-0104", "+1-555-0105"
    };

    private final DeliveryRepository deliveries;
    private final OrderServiceClient orderService;

    public DeliveryService(DeliveryRepository deliveries, OrderServiceClient orderService) {
        this.deliveries = deliveries;
        this.orderService = orderService;
    }

    /**
     * Creates a delivery for an existing order. Looks up the order via Feign
     * to snapshot the data Delivery needs (customer name, addresses).
     *
     * <p>Phase 7: callable directly via {@code POST /api/internal/deliveries}.
     * Phase 8: this same logic moves into an {@code OrderPlacedEvent} consumer.
     */
    @Transactional
    public DeliveryResponse createForOrder(Long orderId) {
        if (deliveries.existsByOrderId(orderId)) {
            throw new DuplicateResourceException(
                    "Delivery already exists for orderId=" + orderId);
        }

        OrderSummary order = orderService.getById(orderId);

        int driverIndex = ThreadLocalRandom.current().nextInt(DRIVERS.length);

        Delivery delivery = Delivery.builder()
                .orderId(order.id())
                .status(Delivery.DeliveryStatus.ASSIGNED)
                .driverName(DRIVERS[driverIndex])
                .driverPhone(PHONES[driverIndex])
                .pickupAddress(order.restaurantAddress())
                .deliveryAddress(order.deliveryAddress())
                .customerName(order.customerName())
                .assignedAt(LocalDateTime.now())
                .build();

        Delivery saved = deliveries.save(delivery);

        log.info("Delivery #{} assigned to {} for order #{}",
                saved.getId(), DRIVERS[driverIndex], saved.getOrderId());

        // Phase 8 will publish DeliveryStatusUpdatedEvent (ASSIGNED) here
        // so Order Service can advance its own status.

        return DeliveryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getById(Long id) {
        return DeliveryResponse.from(findDelivery(id));
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getByOrderId(Long orderId) {
        Delivery delivery = deliveries.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "orderId", orderId));
        return DeliveryResponse.from(delivery);
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getByStatus(String status) {
        Delivery.DeliveryStatus s = Delivery.DeliveryStatus.valueOf(status.toUpperCase());
        return deliveries.findByStatus(s).stream()
                .map(DeliveryResponse::from).toList();
    }

    @Transactional
    public DeliveryResponse updateStatus(Long deliveryId, String status) {
        Delivery delivery = findDelivery(deliveryId);
        Delivery.DeliveryStatus newStatus = Delivery.DeliveryStatus.valueOf(status.toUpperCase());

        delivery.setStatus(newStatus);
        switch (newStatus) {
            case PICKED_UP -> delivery.setPickedUpAt(LocalDateTime.now());
            case DELIVERED -> delivery.setDeliveredAt(LocalDateTime.now());
            default -> {}
        }

        Delivery saved = deliveries.save(delivery);

        log.info("Delivery #{} status changed to {}", deliveryId, newStatus);

        // Phase 8 will publish DeliveryStatusUpdatedEvent here so Order
        // Service can advance its order status (OUT_FOR_DELIVERY, DELIVERED, etc.)
        // without us calling Order Service directly.

        return DeliveryResponse.from(saved);
    }

    private Delivery findDelivery(Long id) {
        return deliveries.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "id", id));
    }
}
