package com.fooddelivery.order.events;

import com.fooddelivery.shared.events.EventTopology;
import com.fooddelivery.shared.events.OrderCancelledEvent;
import com.fooddelivery.shared.events.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ships order domain events to RabbitMQ <strong>after</strong> the placing /
 * cancelling transaction commits. The service publishes the event as a Spring
 * {@code ApplicationEvent}; this listener catches it at
 * {@link TransactionPhase#AFTER_COMMIT} and forwards to the broker.
 *
 * <p>If the JVM crashes between commit and publish, the event is lost — the
 * standard "publish-after-commit" tradeoff. A transactional outbox would fix
 * it; out of scope for this project.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Publishing OrderPlacedEvent eventId={} orderId={}",
                event.eventId(), event.orderId());
        rabbitTemplate.convertAndSend(
                EventTopology.EXCHANGE,
                EventTopology.RoutingKey.ORDER_PLACED,
                event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Publishing OrderCancelledEvent eventId={} orderId={}",
                event.eventId(), event.orderId());
        rabbitTemplate.convertAndSend(
                EventTopology.EXCHANGE,
                EventTopology.RoutingKey.ORDER_CANCELLED,
                event);
    }
}
