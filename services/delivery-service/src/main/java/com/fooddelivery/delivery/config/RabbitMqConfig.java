package com.fooddelivery.delivery.config;

import com.fooddelivery.shared.events.EventTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Declares the RabbitMQ topology Delivery Service participates in:
 *
 * <ul>
 *   <li>Main + DLX topic exchanges (shared with Order Service).</li>
 *   <li>Consumer queue {@code delivery.order-events} bound to BOTH
 *       {@code order.placed} and {@code order.cancelled}.</li>
 *   <li>DLQ {@code delivery.order-events.dlq} bound to the DLX
 *       with the same two routing keys.</li>
 * </ul>
 */
@Configuration
public class RabbitMqConfig {

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

    // ---- Queue owned by Delivery Service ----

    @Bean
    public Queue deliveryOrderEventsQueue() {
        // Two routing keys land in the same queue. The DLX target uses the
        // wildcard 'order.*' so either kind of failure flows to the DLQ.
        return QueueBuilder.durable(EventTopology.Queue.DELIVERY_ORDER_EVENTS)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", EventTopology.DLX_EXCHANGE
                ))
                .build();
    }

    @Bean
    public Queue deliveryOrderEventsDlq() {
        return QueueBuilder.durable(EventTopology.Queue.DELIVERY_ORDER_EVENTS_DLQ).build();
    }

    // ---- Bindings ----

    @Bean
    public Binding bindOrderPlacedToDelivery(Queue deliveryOrderEventsQueue,
                                             TopicExchange mainExchange) {
        return BindingBuilder.bind(deliveryOrderEventsQueue)
                .to(mainExchange)
                .with(EventTopology.RoutingKey.ORDER_PLACED);
    }

    @Bean
    public Binding bindOrderCancelledToDelivery(Queue deliveryOrderEventsQueue,
                                                TopicExchange mainExchange) {
        return BindingBuilder.bind(deliveryOrderEventsQueue)
                .to(mainExchange)
                .with(EventTopology.RoutingKey.ORDER_CANCELLED);
    }

    @Bean
    public Binding bindDlqOrderPlaced(Queue deliveryOrderEventsDlq,
                                      TopicExchange dlxExchange) {
        return BindingBuilder.bind(deliveryOrderEventsDlq)
                .to(dlxExchange)
                .with(EventTopology.RoutingKey.ORDER_PLACED);
    }

    @Bean
    public Binding bindDlqOrderCancelled(Queue deliveryOrderEventsDlq,
                                         TopicExchange dlxExchange) {
        return BindingBuilder.bind(deliveryOrderEventsDlq)
                .to(dlxExchange)
                .with(EventTopology.RoutingKey.ORDER_CANCELLED);
    }

    // ---- JSON message conversion ----

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
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
