package com.fooddelivery.delivery.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Manual "create delivery for this order" request used in Phase 7 for
 * standalone testing. Phase 8 replaces this with the {@code OrderPlacedEvent}
 * consumer — same logic, different trigger.
 */
public record CreateDeliveryRequest(
        @NotNull Long orderId
) {}
