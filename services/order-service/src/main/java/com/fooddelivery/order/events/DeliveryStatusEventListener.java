package com.fooddelivery.order.events;

import com.fooddelivery.order.model.Order;
import com.fooddelivery.order.model.ProcessedEvent;
import com.fooddelivery.order.repository.OrderRepository;
import com.fooddelivery.order.repository.ProcessedEventRepository;
import com.fooddelivery.shared.events.DeliveryStatusUpdatedEvent;
import com.fooddelivery.shared.events.EventTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Consumes {@link DeliveryStatusUpdatedEvent} from RabbitMQ and advances the
 * matching order's status. Replaces the monolith's direct
 * {@code DeliveryService.updateStatus → order.setStatus} cross-domain write.
 *
 * <p>Idempotent: every event id is recorded in {@code processed_events_order}
 * inside the same transaction as the order update. RabbitMQ at-least-once
 * delivery means we may see the same event twice — the second one is a no-op.
 */
@Component
public class DeliveryStatusEventListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryStatusEventListener.class);

    private final OrderRepository orders;
    private final ProcessedEventRepository processedEvents;

    public DeliveryStatusEventListener(OrderRepository orders,
                                       ProcessedEventRepository processedEvents) {
        this.orders = orders;
        this.processedEvents = processedEvents;
    }

    @RabbitListener(queues = EventTopology.Queue.ORDER_DELIVERY_EVENTS)
    @Transactional
    public void onDeliveryStatusUpdated(DeliveryStatusUpdatedEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Duplicate DeliveryStatusUpdatedEvent eventId={} — skipping",
                    event.eventId());
            return;
        }

        Optional<Order> maybeOrder = orders.findById(event.orderId());
        if (maybeOrder.isEmpty()) {
            log.warn("DeliveryStatusUpdatedEvent for unknown orderId={} — recording as processed",
                    event.orderId());
        } else {
            Order order = maybeOrder.get();
            Order.OrderStatus newStatus = mapDeliveryStatus(event.newStatus(), order.getStatus());
            if (newStatus != order.getStatus()) {
                log.info("Order {} status: {} → {} (driven by delivery {})",
                        order.getId(), order.getStatus(), newStatus, event.deliveryId());
                order.setStatus(newStatus);
                orders.save(order);
            }
        }

        processedEvents.save(ProcessedEvent.builder()
                .eventId(event.eventId())
                .eventType(event.eventType())
                .processedAt(Instant.now())
                .build());
    }

    /**
     * Delivery status -> Order status. Returns the current status if the
     * incoming event shouldn't advance the order.
     */
    private Order.OrderStatus mapDeliveryStatus(String deliveryStatus,
                                                Order.OrderStatus currentOrderStatus) {
        return switch (deliveryStatus) {
            case "ASSIGNED"   -> currentOrderStatus == Order.OrderStatus.PLACED
                                 ? Order.OrderStatus.CONFIRMED
                                 : currentOrderStatus;
            case "PICKED_UP", "IN_TRANSIT" -> Order.OrderStatus.OUT_FOR_DELIVERY;
            case "DELIVERED"  -> Order.OrderStatus.DELIVERED;
            case "FAILED"     -> currentOrderStatus; // order cancellation handled separately
            default -> currentOrderStatus;
        };
    }
}
