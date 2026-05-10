package com.fooddelivery.service;

import com.fooddelivery.dto.DeliveryResponse;
import com.fooddelivery.exception.ResourceNotFoundException;
import com.fooddelivery.model.*;
import com.fooddelivery.repository.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MONOLITH COUPLING: DeliveryService directly accesses Order entity
 * (and through it, Customer and Restaurant entities).
 *
 * In microservices:
 *  - Delivery Service subscribes to OrderPlacedEvent via RabbitMQ
 *  - Stores orderId, customerAddress, restaurantAddress as local data
 *  - Publishes DeliveryStatusUpdatedEvent when status changes
 *  - No direct dependency on Order, Customer, or Restaurant entities
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final DeliveryRepository deliveryRepository;

    // Simulated driver pool — in reality this would be its own service
    private static final String[] DRIVERS = {
            "Carlos Martinez", "Sarah Johnson", "Mike Chen", "Priya Patel", "James Wilson"
    };
    private static final String[] PHONES = {
            "+1-555-0101", "+1-555-0102", "+1-555-0103", "+1-555-0104", "+1-555-0105"
    };

    public DeliveryService(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    /**
     * MONOLITH PROBLEM: Called SYNCHRONOUSLY from OrderService.placeOrder().
     * This blocks the order response until delivery is assigned.
     *
     * In microservices: Delivery Service consumes OrderPlacedEvent
     * from RabbitMQ and creates the delivery ASYNCHRONOUSLY.
     */
    @Transactional
    public void createDeliveryForOrder(Order order) {
        int driverIndex = (int) (Math.random() * DRIVERS.length);

        Delivery delivery = Delivery.builder()
                .order(order)  // MONOLITH: direct entity reference
                .status(Delivery.DeliveryStatus.ASSIGNED)
                .driverName(DRIVERS[driverIndex])
                .driverPhone(PHONES[driverIndex])
                // MONOLITH: accessing Customer and Restaurant through Order entity
                .pickupAddress(order.getRestaurant().getAddress())
                .deliveryAddress(order.getDeliveryAddress())
                .assignedAt(LocalDateTime.now())
                .build();

        deliveryRepository.save(delivery);

        // MONOLITH PROBLEM: Synchronous "notification" log
        // In microservices, publish DeliveryAssignedEvent to RabbitMQ
        log.info("NOTIFICATION: Delivery assigned to {} for order #{} — "
                        + "Customer: {} {}, Restaurant: {}",
                DRIVERS[driverIndex],
                order.getId(),
                order.getCustomer().getFirstName(),  // CROSS-DOMAIN
                order.getCustomer().getLastName(),    // CROSS-DOMAIN
                order.getRestaurant().getName());     // CROSS-DOMAIN
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getByOrderId(Long orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "orderId", orderId));
        return DeliveryResponse.fromEntity(delivery);
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getById(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "id", deliveryId));
        return DeliveryResponse.fromEntity(delivery);
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getByStatus(String status) {
        Delivery.DeliveryStatus deliveryStatus = Delivery.DeliveryStatus.valueOf(status.toUpperCase());
        return deliveryRepository.findByStatus(deliveryStatus)
                .stream().map(DeliveryResponse::fromEntity).toList();
    }

    @Transactional
    public DeliveryResponse updateStatus(Long deliveryId, String status) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "id", deliveryId));

        Delivery.DeliveryStatus newStatus = Delivery.DeliveryStatus.valueOf(status.toUpperCase());
        delivery.setStatus(newStatus);

        switch (newStatus) {
            case PICKED_UP -> delivery.setPickedUpAt(LocalDateTime.now());
            case DELIVERED -> {
                delivery.setDeliveredAt(LocalDateTime.now());
                // MONOLITH: directly updating Order status from Delivery domain
                delivery.getOrder().setStatus(Order.OrderStatus.DELIVERED);
            }
            default -> {}
        }

        // MONOLITH PROBLEM: synchronous notification
        log.info("NOTIFICATION: Delivery #{} status changed to {} — "
                        + "Customer: {} {}",
                deliveryId, newStatus,
                delivery.getOrder().getCustomer().getFirstName(),  // CROSS-DOMAIN
                delivery.getOrder().getCustomer().getLastName());  // CROSS-DOMAIN

        return DeliveryResponse.fromEntity(deliveryRepository.save(delivery));
    }

    @Transactional
    public void cancelDelivery(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "id", deliveryId));
        delivery.setStatus(Delivery.DeliveryStatus.FAILED);
        deliveryRepository.save(delivery);

        log.info("NOTIFICATION: Delivery #{} cancelled", deliveryId);
    }
}
