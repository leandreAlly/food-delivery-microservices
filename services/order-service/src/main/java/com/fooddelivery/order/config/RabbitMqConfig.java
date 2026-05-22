package com.fooddelivery.order.config;

import com.fooddelivery.shared.events.EventTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Declares the RabbitMQ topology Order Service participates in:
 *
 * <ul>
 *   <li>Main topic exchange ({@code food-delivery.events}) — shared with Delivery.</li>
 *   <li>DLX topic exchange ({@code food-delivery.events.dlx}).</li>
 *   <li>Consumer queue {@code order.delivery-events} bound to {@code delivery.status-updated}.</li>
 *   <li>DLQ {@code order.delivery-events.dlq} bound to the DLX with the same routing key.</li>
 * </ul>
 *
 * <p>Declarations are idempotent on the broker side — Delivery Service
 * declares the same exchanges and the first one to arrive wins. Other
 * declarations just verify.
 */
@Configuration
public class RabbitMqConfig {

    // ---- Exchanges (declared by both services; broker dedupes) ----

    @Bean
    public TopicExchange mainExchange() {
        return ExchangeBuilder.topicExchange(EventTopology.EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public TopicExchange dlxExchange() {
        return ExchangeBuilder.topicExchange(EventTopology.DLX_EXCHANGE)
                .durable(true).build();
    }

    // ---- Queues owned by Order Service ----

    @Bean
    public Queue orderDeliveryEventsQueue() {
        return QueueBuilder.durable(EventTopology.Queue.ORDER_DELIVERY_EVENTS)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", EventTopology.DLX_EXCHANGE,
                        "x-dead-letter-routing-key", EventTopology.RoutingKey.DELIVERY_STATUS_UPDATED
                ))
                .build();
    }

    @Bean
    public Queue orderDeliveryEventsDlq() {
        return QueueBuilder.durable(EventTopology.Queue.ORDER_DELIVERY_EVENTS_DLQ).build();
    }

    // ---- Bindings ----

    @Bean
    public Binding bindOrderDeliveryEvents(Queue orderDeliveryEventsQueue,
                                           TopicExchange mainExchange) {
        return BindingBuilder.bind(orderDeliveryEventsQueue)
                .to(mainExchange)
                .with(EventTopology.RoutingKey.DELIVERY_STATUS_UPDATED);
    }

    @Bean
    public Binding bindOrderDeliveryEventsDlq(Queue orderDeliveryEventsDlq,
                                              TopicExchange dlxExchange) {
        return BindingBuilder.bind(orderDeliveryEventsDlq)
                .to(dlxExchange)
                .with(EventTopology.RoutingKey.DELIVERY_STATUS_UPDATED);
    }

    // ---- JSON message conversion ----

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        // Trusted package allows the type mapper to instantiate the
        // concrete event class on the consumer side via the __TypeId__
        // header that the producer's converter sets.
        return new Jackson2JsonMessageConverter("com.fooddelivery.shared.events");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setExchange(EventTopology.EXCHANGE);
        return template;
    }
}
