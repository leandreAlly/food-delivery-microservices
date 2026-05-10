# common-events

The cross-service event contract — records that fly across RabbitMQ. Anything in here is shared *by contract*; changing a field shape is a breaking change for at least one consumer.

## Events

| Event | Routing key | Published by | Consumed by |
|---|---|---|---|
| `OrderPlacedEvent` | `order.placed` | Order Service | Delivery Service |
| `OrderCancelledEvent` | `order.cancelled` | Order Service | Delivery Service |
| `DeliveryStatusUpdatedEvent` | `delivery.status-updated` | Delivery Service | Order Service |

## Topology (`EventTopology`)

```
exchange: food-delivery.events           (topic)
exchange: food-delivery.events.dlx       (topic — dead letter)

queues:
  delivery.order-events       ← order.placed, order.cancelled
  order.delivery-events       ← delivery.status-updated
  delivery.order-events.dlq   ← failures from delivery.order-events
  order.delivery-events.dlq   ← failures from order.delivery-events
```

## Idempotency

Every event has an `eventId` (UUID). Consumers insert `(eventId, eventType, processed_at)` into a small `processed_events_*` table inside their own DB before doing the work. If the same event is delivered twice (RabbitMQ at-least-once), the second insert fails the unique constraint and the consumer no-ops.

## Build & install

```bash
cd shared/common-events
mvn clean install
```
