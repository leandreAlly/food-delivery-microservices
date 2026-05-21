package com.fooddelivery.order.dto;

import com.fooddelivery.order.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        BigDecimal totalAmount,
        BigDecimal deliveryFee,
        String deliveryAddress,
        String specialInstructions,
        Long customerId,
        String customerName,
        Long restaurantId,
        String restaurantName,
        String restaurantAddress,
        LocalDateTime createdAt,
        LocalDateTime estimatedDeliveryTime,
        List<Item> items
) {
    public record Item(
            Long id,
            Long menuItemId,
            String menuItemName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal,
            String specialInstructions
    ) {}

    public static OrderResponse from(Order o) {
        List<Item> items = o.getItems().stream()
                .map(i -> new Item(
                        i.getId(), i.getMenuItemId(), i.getMenuItemName(),
                        i.getQuantity(), i.getUnitPrice(), i.getSubtotal(),
                        i.getSpecialInstructions()))
                .toList();
        return new OrderResponse(
                o.getId(), o.getStatus().name(), o.getTotalAmount(), o.getDeliveryFee(),
                o.getDeliveryAddress(), o.getSpecialInstructions(),
                o.getCustomerId(), o.getCustomerName(),
                o.getRestaurantId(), o.getRestaurantName(), o.getRestaurantAddress(),
                o.getCreatedAt(), o.getEstimatedDeliveryTime(), items);
    }
}
