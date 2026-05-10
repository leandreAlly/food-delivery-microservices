package com.fooddelivery.shared.events;

/**
 * RabbitMQ exchange, queue, and routing key names — kept in one place so all
 * publishers and consumers agree. The topology is declared in each service's
 * RabbitConfig and bound to these constants.
 */
public final class EventTopology {

    public static final String EXCHANGE     = "food-delivery.events";
    public static final String DLX_EXCHANGE = "food-delivery.events.dlx";

    public static final class RoutingKey {
        public static final String ORDER_PLACED             = "order.placed";
        public static final String ORDER_CANCELLED          = "order.cancelled";
        public static final String DELIVERY_STATUS_UPDATED  = "delivery.status-updated";

        private RoutingKey() {}
    }

    public static final class Queue {
        public static final String DELIVERY_ORDER_EVENTS    = "delivery.order-events";
        public static final String ORDER_DELIVERY_EVENTS    = "order.delivery-events";

        public static final String DELIVERY_ORDER_EVENTS_DLQ = "delivery.order-events.dlq";
        public static final String ORDER_DELIVERY_EVENTS_DLQ = "order.delivery-events.dlq";

        private Queue() {}
    }

    private EventTopology() {}
}
