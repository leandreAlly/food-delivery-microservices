package com.fooddelivery.delivery.events;

import com.fooddelivery.shared.events.DeliveryStatusUpdatedEvent;
import com.fooddelivery.shared.events.EventTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ships {@link DeliveryStatusUpdatedEvent}s to RabbitMQ after the delivery
 * write commits. Order Service consumes these to advance its own status
 * machine without us calling Order directly.
 */
@Component
public class DeliveryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public DeliveryEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeliveryStatusUpdated(DeliveryStatusUpdatedEvent event) {
        log.info("Publishing DeliveryStatusUpdatedEvent eventId={} deliveryId={} status={}",
                event.eventId(), event.deliveryId(), event.newStatus());
        rabbitTemplate.convertAndSend(
                EventTopology.EXCHANGE,
                EventTopology.RoutingKey.DELIVERY_STATUS_UPDATED,
                event);
    }
}
