package com.fooddelivery.delivery.service;

import com.fooddelivery.delivery.client.OrderServiceClient;
import com.fooddelivery.delivery.client.dto.OrderSummary;
import com.fooddelivery.delivery.dto.DeliveryResponse;
import com.fooddelivery.delivery.model.Delivery;
import com.fooddelivery.delivery.repository.DeliveryRepository;
import com.fooddelivery.shared.events.DeliveryStatusUpdatedEvent;
import com.fooddelivery.shared.events.OrderPlacedEvent;
import com.fooddelivery.shared.web.exception.DuplicateResourceException;
import com.fooddelivery.shared.web.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
    private final ApplicationEventPublisher applicationEventPublisher;

    public DeliveryService(DeliveryRepository deliveries,
                           OrderServiceClient orderService,
                           ApplicationEventPublisher applicationEventPublisher) {
        this.deliveries = deliveries;
        this.orderService = orderService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Phase 7 manual creation path — fetches the order via Feign and snapshots
     * its data into a new delivery. Now an admin / replay tool; the primary
     * creation path is {@link #createFromOrderPlaced} driven by RabbitMQ.
     */
    @Transactional
    public DeliveryResponse createForOrder(Long orderId) {
        if (deliveries.existsByOrderId(orderId)) {
            throw new DuplicateResourceException(
                    "Delivery already exists for orderId=" + orderId);
        }

        OrderSummary order = orderService.getById(orderId);
        Delivery saved = assignAndSave(
                order.id(),
                order.restaurantAddress(),
                order.deliveryAddress(),
                order.customerName());

        publishStatusEvent(saved);
        return DeliveryResponse.from(saved);
    }

    /**
     * Event-driven creation path — called by {@code OrderEventListener} when
     * an {@code OrderPlacedEvent} arrives. The event already carries the
     * snapshot data we need, so NO Feign call to Order Service.
     */
    @Transactional
    public DeliveryResponse createFromOrderPlaced(OrderPlacedEvent event) {
        if (deliveries.existsByOrderId(event.orderId())) {
            throw new DuplicateResourceException(
                    "Delivery already exists for orderId=" + event.orderId());
        }

        Delivery saved = assignAndSave(
                event.orderId(),
                event.restaurantAddress(),
                event.deliveryAddress(),
                event.customerName());

        publishStatusEvent(saved);
        return DeliveryResponse.from(saved);
    }

    /**
     * Marks an in-flight delivery as FAILED when the corresponding order is
     * cancelled. Called by {@code OrderEventListener} on
     * {@code OrderCancelledEvent}.
     */
    @Transactional
    public void markFailedForOrder(Long orderId) {
        deliveries.findByOrderId(orderId).ifPresentOrElse(
                delivery -> {
                    if (delivery.getStatus() == Delivery.DeliveryStatus.DELIVERED) {
                        log.info("Order {} cancelled but delivery {} already DELIVERED — ignoring",
                                orderId, delivery.getId());
                        return;
                    }
                    delivery.setStatus(Delivery.DeliveryStatus.FAILED);
                    deliveries.save(delivery);
                    log.info("Delivery {} marked FAILED due to order {} cancellation",
                            delivery.getId(), orderId);
                    publishStatusEvent(delivery);
                },
                () -> log.info("OrderCancelledEvent for orderId={} arrived before any delivery existed",
                        orderId)
        );
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

        publishStatusEvent(saved);

        return DeliveryResponse.from(saved);
    }

    // ---- internal helpers ----

    private Delivery assignAndSave(Long orderId,
                                   String restaurantAddress,
                                   String deliveryAddress,
                                   String customerName) {
        int driverIndex = ThreadLocalRandom.current().nextInt(DRIVERS.length);

        Delivery delivery = Delivery.builder()
                .orderId(orderId)
                .status(Delivery.DeliveryStatus.ASSIGNED)
                .driverName(DRIVERS[driverIndex])
                .driverPhone(PHONES[driverIndex])
                .pickupAddress(restaurantAddress)
                .deliveryAddress(deliveryAddress)
                .customerName(customerName)
                .assignedAt(LocalDateTime.now())
                .build();

        Delivery saved = deliveries.save(delivery);
        log.info("Delivery #{} assigned to {} for order #{}",
                saved.getId(), DRIVERS[driverIndex], saved.getOrderId());
        return saved;
    }

    private void publishStatusEvent(Delivery delivery) {
        applicationEventPublisher.publishEvent(new DeliveryStatusUpdatedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStatus().name()
        ));
    }

    private Delivery findDelivery(Long id) {
        return deliveries.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "id", id));
    }
}
