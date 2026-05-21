package com.fooddelivery.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Order Service sends this to validate menu items + get live prices
 * before snapshotting them into order_db.
 */
public record ValidateOrderRequest(
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotNull Long menuItemId,
            @Min(1) int quantity
    ) {}
}
