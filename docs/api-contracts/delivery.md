# Delivery Service API contract

Driver assignment and tracking. Auto-creates a delivery when it receives an `OrderPlacedEvent`.

| | |
|---|---|
| Port | `8084` |
| Owns | `delivery_db` |
| Base paths | `/api/deliveries/**`, `/api/internal/deliveries/**` |
| Publishes | `DeliveryStatusUpdatedEvent` |
| Consumes | `OrderPlacedEvent`, `OrderCancelledEvent` |

## Authenticated endpoints

### `GET /api/deliveries/{id}`

Read a specific delivery.

Response — `200 OK`:
```json
{
  "id": 1,
  "orderId": 1,
  "status": "ASSIGNED",
  "driverName": "Sarah Johnson",
  "driverPhone": "+1-555-0102",
  "pickupAddress": "45 Marina Road",
  "deliveryAddress": "123 Main St",
  "customerName": "John Doe",
  "assignedAt": "2026-05-14T09:00:05",
  "pickedUpAt": null,
  "deliveredAt": null,
  "createdAt": "2026-05-14T09:00:05"
}
```

All `*Address` and `customerName` fields are **snapshots** taken at delivery-creation time — Delivery Service does not call other services on read.

Status values:
- `PENDING` — pre-assignment (currently unused; assignment happens at creation)
- `ASSIGNED` — driver assigned, awaiting pickup
- `PICKED_UP` — driver has the order
- `IN_TRANSIT` — currently unused
- `DELIVERED` — completed
- `FAILED` — order was cancelled or delivery couldn't complete

Errors:
- `404` — no delivery with that ID

### `GET /api/deliveries/order/{orderId}`

Find the delivery for a given order.

Response — `200 OK`: `DeliveryResponse` (same as above).

Errors:
- `404` — no delivery exists for this `orderId` yet (typical if you ask within ~500 ms of placing the order — the `OrderPlacedEvent` hasn't been consumed yet)

### `GET /api/deliveries/status/{status}`

List all deliveries currently in a given status. Useful for a driver-dashboard view ("show me all `ASSIGNED` deliveries").

Path: status value — one of `PENDING` / `ASSIGNED` / `PICKED_UP` / `IN_TRANSIT` / `DELIVERED` / `FAILED`.

Response — `200 OK`: `DeliveryResponse[]`.

Errors:
- `400` — unknown status value

### `PATCH /api/deliveries/{id}/status?status=X`

Driver progress update.

| Status | Side effect |
|---|---|
| `PICKED_UP` | Sets `pickedUpAt`, publishes `DeliveryStatusUpdatedEvent` (→ Order Service advances order to `OUT_FOR_DELIVERY`) |
| `DELIVERED` | Sets `deliveredAt`, publishes event (→ order becomes `DELIVERED`) |
| Other values | Just sets the new status + publishes event |

Response — `200 OK`: updated `DeliveryResponse`.

Errors:
- `400` — unknown status value
- `404` — delivery doesn't exist

**State-transition validation is intentionally lax** — the endpoint accepts any → any. A real platform would enforce `ASSIGNED → PICKED_UP → DELIVERED`.

## Internal endpoints (not gateway-routed)

### `POST /api/internal/deliveries`

**Phase 7 bridge.** Manually create a delivery for an existing `orderId`. Phase 8 moved the primary creation path into the `OrderPlacedEvent` consumer; this endpoint stays as an admin/replay tool.

Request:
```json
{ "orderId": 1 }
```

Effect:
1. Feign → `order-service` `GET /api/internal/orders/{id}` to fetch snapshot data
2. Assign a random driver from the hardcoded pool
3. Insert the delivery with status `ASSIGNED`
4. Publish `DeliveryStatusUpdatedEvent` (which order-service uses to advance to `CONFIRMED`)

Response — `201 Created`: `DeliveryResponse`.

Errors:
- `409` — a delivery already exists for this `orderId` (unique constraint)
- `503` — Order Service is unavailable
- `404` — `orderId` doesn't exist

## Events emitted

| Event | Routing key | When |
|---|---|---|
| `DeliveryStatusUpdatedEvent` | `delivery.status-updated` | Every time delivery status changes (assignment, pickup, delivery, failure) — AFTER_COMMIT |

## Events consumed

| Event | From queue | Effect |
|---|---|---|
| `OrderPlacedEvent` | `delivery.order-events` | Auto-create delivery with snapshot data from the event payload (no Feign call) |
| `OrderCancelledEvent` | `delivery.order-events` | Mark the matching delivery as `FAILED` (unless it's already `DELIVERED`) |

## Driver pool

Currently hardcoded — same names/phones as the monolith:
```
Carlos Martinez   +1-555-0101
Sarah Johnson     +1-555-0102
Mike Chen         +1-555-0103
Priya Patel       +1-555-0104
James Wilson      +1-555-0105
```

Random selection on each delivery. A real platform would have its own Driver Service.

## Error envelope

See [`README.md`](./README.md). Delivery Service maps:

| Exception | Status |
|---|---|
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| `OrderServiceUnavailableException` | 503 |
| `IllegalArgumentException` (bad status value) | 400 |
