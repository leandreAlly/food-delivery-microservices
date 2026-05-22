package com.fooddelivery.delivery.events;

import com.fooddelivery.delivery.model.ProcessedEvent;
import com.fooddelivery.delivery.repository.ProcessedEventRepository;
import com.fooddelivery.delivery.service.DeliveryService;
import com.fooddelivery.shared.events.EventTopology;
import com.fooddelivery.shared.events.OrderCancelledEvent;
import com.fooddelivery.shared.events.OrderPlacedEvent;
import com.fooddelivery.shared.web.exception.DuplicateResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Consumes order domain events from RabbitMQ.
 *
 * <p>{@code @RabbitListener} on the class + {@code @RabbitHandler} per
 * concrete event type lets Jackson dispatch by JSON payload class
 * (via the {@code __TypeId__} header set by the producer).
 *
 * <p>Idempotency: every {@code eventId} is recorded in
 * {@code processed_events_delivery} inside the same transaction as the
 * downstream work. Duplicate deliveries (PK violation) cause the
 * transaction to roll back, and the message is re-routed through the
 * retry interceptor before eventually landing in the DLQ.
 */
@Component
@RabbitListener(queues = EventTopology.Queue.DELIVERY_ORDER_EVENTS)
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final DeliveryService deliveryService;
    private final ProcessedEventRepository processedEvents;

    public OrderEventListener(DeliveryService deliveryService,
                              ProcessedEventRepository processedEvents) {
        this.deliveryService = deliveryService;
        this.processedEvents = processedEvents;
    }

    @RabbitHandler
    @Transactional
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Duplicate OrderPlacedEvent eventId={} — skipping", event.eventId());
            return;
        }

        try {
            deliveryService.createFromOrderPlaced(event);
        } catch (DuplicateResourceException ex) {
            // A previous successful run created the delivery; just mark
            // the event as processed and move on rather than DLQ'ing.
            log.warn("Delivery already exists for orderId={} (eventId={}) — recording as processed",
                    event.orderId(), event.eventId());
        }

        processedEvents.save(ProcessedEvent.builder()
                .eventId(event.eventId())
                .eventType(event.eventType())
                .processedAt(Instant.now())
                .build());
    }

    @RabbitHandler
    @Transactional
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Duplicate OrderCancelledEvent eventId={} — skipping", event.eventId());
            return;
        }

        deliveryService.markFailedForOrder(event.orderId());

        processedEvents.save(ProcessedEvent.builder()
                .eventId(event.eventId())
                .eventType(event.eventType())
                .processedAt(Instant.now())
                .build());
    }

    /**
     * Catch-all for messages whose {@code __TypeId__} header points at a
     * class not handled above (e.g. a new event added to common-events
     * we haven't taught this service about). Letting them silently DLQ
     * is safer than throwing a generic error nobody can diagnose.
     */
    @RabbitHandler(isDefault = true)
    public void onUnknown(Object payload) {
        log.error("Unknown event type received on {}: {}",
                EventTopology.Queue.DELIVERY_ORDER_EVENTS,
                payload == null ? "null" : payload.getClass().getName());
        throw new IllegalArgumentException(
                "Unsupported event type — routing to DLQ");
    }
}
