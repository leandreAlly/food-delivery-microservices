package com.fooddelivery.delivery.dto;

import com.fooddelivery.delivery.model.Delivery;

import java.time.LocalDateTime;

public record DeliveryResponse(
        Long id,
        Long orderId,
        String status,
        String driverName,
        String driverPhone,
        String pickupAddress,
        String deliveryAddress,
        String customerName,
        LocalDateTime assignedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt
) {
    public static DeliveryResponse from(Delivery d) {
        return new DeliveryResponse(
                d.getId(), d.getOrderId(), d.getStatus().name(),
                d.getDriverName(), d.getDriverPhone(),
                d.getPickupAddress(), d.getDeliveryAddress(), d.getCustomerName(),
                d.getAssignedAt(), d.getPickedUpAt(), d.getDeliveredAt(),
                d.getCreatedAt());
    }
}
