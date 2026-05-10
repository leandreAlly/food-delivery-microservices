package com.fooddelivery.dto;

import com.fooddelivery.model.Delivery;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeliveryResponse {
    private Long id;
    private String status;
    private String driverName;
    private String driverPhone;
    private String pickupAddress;
    private String deliveryAddress;
    private LocalDateTime assignedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;

    // MONOLITH: cross-domain order info embedded
    private Long orderId;
    private String orderStatus;
    private Long customerId;
    private String customerName;
    private String restaurantName;

    public static DeliveryResponse fromEntity(Delivery d) {
        DeliveryResponse dto = new DeliveryResponse();
        dto.setId(d.getId());
        dto.setStatus(d.getStatus().name());
        dto.setDriverName(d.getDriverName());
        dto.setDriverPhone(d.getDriverPhone());
        dto.setPickupAddress(d.getPickupAddress());
        dto.setDeliveryAddress(d.getDeliveryAddress());
        dto.setAssignedAt(d.getAssignedAt());
        dto.setPickedUpAt(d.getPickedUpAt());
        dto.setDeliveredAt(d.getDeliveredAt());
        dto.setCreatedAt(d.getCreatedAt());

        // MONOLITH: cross-domain entity traversal
        dto.setOrderId(d.getOrder().getId());
        dto.setOrderStatus(d.getOrder().getStatus().name());
        dto.setCustomerId(d.getOrder().getCustomer().getId());
        dto.setCustomerName(d.getOrder().getCustomer().getFirstName()
                + " " + d.getOrder().getCustomer().getLastName());
        dto.setRestaurantName(d.getOrder().getRestaurant().getName());
        return dto;
    }
}
