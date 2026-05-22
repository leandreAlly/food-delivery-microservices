# Order Service API contract

Order placement and lifecycle. Calls Customer Service + Restaurant Service via Feign at write time, then publishes events.

| | |
|---|---|
| Port | `8083` |
| Owns | `order_db` (Order + OrderItem aggregate) |
| Base paths | `/api/orders/**`, `/api/internal/orders/**` |
| Publishes | `OrderPlacedEvent`, `OrderCancelledEvent` |
| Consumes | `DeliveryStatusUpdatedEvent` |

## Authenticated endpoints

### `POST /api/orders`

Place an order.

**What this triggers, in order**:
1. Feign → `customer-service` for delivery address + customer name
2. Feign → `restaurant-service` `validateOrder` for live prices
3. Persist order with snapshot data (`customerName`, `restaurantName`, `menuItemName`, `unitPrice`, …)
4. Publish `OrderPlacedEvent` to RabbitMQ (AFTER_COMMIT)

**Also**: through the gateway, this endpoint is rate-limited (1 req/sec, burst of 3, keyed by `X-User-Id`).

Request:
```json
{
  "restaurantId": 1,
  "items": [
    { "menuItemId": 1, "quantity": 2, "specialInstructions": "extra hot" },
    { "menuItemId": 3, "quantity": 1 }
  ],
  "deliveryAddress": "45 Different Address St",
  "specialInstructions": "Leave at door"
}
```

| Field | Required | Notes |
|---|---|---|
| `restaurantId` | yes | Must exist in restaurant-service |
| `items` | yes, ≥ 1 | Each must reference an available menu item |
| `items[].menuItemId` | yes | |
| `items[].quantity` | yes, ≥ 1 | |
| `items[].specialInstructions` | optional | |
| `deliveryAddress` | optional | Falls back to the customer's stored `deliveryAddress` |
| `specialInstructions` | optional | Order-level note |

Response — `201 Created`:
```json
{
  "id": 1,
  "status": "PLACED",
  "totalAmount": 6500.00,
  "deliveryFee": 2.99,
  "deliveryAddress": "45 Different Address St",
  "specialInstructions": "Leave at door",
  "customerId": 1,
  "customerName": "John Doe",
  "restaurantId": 1,
  "restaurantName": "Mama's Kitchen",
  "restaurantAddress": "45 Marina Road",
  "createdAt": "2026-05-14T09:00:00",
  "estimatedDeliveryTime": "2026-05-14T09:35:00",
  "items": [
    {
      "id": 1,
      "menuItemId": 1,
      "menuItemName": "Jollof Rice",
      "quantity": 2,
      "unitPrice": 2500.00,
      "subtotal": 5000.00,
      "specialInstructions": "extra hot"
    },
    {
      "id": 2,
      "menuItemId": 3,
      "menuItemName": "Suya",
      "quantity": 1,
      "unitPrice": 1500.00,
      "subtotal": 1500.00,
      "specialInstructions": null
    }
  ]
}
```

Errors:
- `400` — invalid body, or a referenced menu item is unavailable, or the restaurant is inactive
- `404` — restaurant or menu item doesn't exist
- `429` — gateway rate limit tripped
- `503` — Customer Service or Restaurant Service is unavailable

### `GET /api/orders/{id}`

Read a specific order. Status may have advanced since placement (driven by inbound `DeliveryStatusUpdatedEvent`).

Response — `200 OK`: same `OrderResponse` shape as `POST`.

Order statuses you may see:
- `PLACED` — just created, no delivery yet
- `CONFIRMED` — delivery has been assigned (driven by `DeliveryStatusUpdatedEvent` with `ASSIGNED`)
- `OUT_FOR_DELIVERY` — driver picked it up (`PICKED_UP` or `IN_TRANSIT`)
- `DELIVERED` — completed
- `CANCELLED` — cancelled by the customer
- `PREPARING`, `READY_FOR_PICKUP` — currently only via the manual `PATCH /status` endpoint

Errors:
- `404` — no order with that ID

### `GET /api/orders/my-orders`

Return all orders for the authenticated caller, newest first.

Response — `200 OK`: `OrderResponse[]`.

### `GET /api/orders/restaurant/{restaurantId}`

Return all orders for a given restaurant, newest first.

Response — `200 OK`: `OrderResponse[]`.

**Authorization caveat**: any authenticated user can call this. A real platform would restrict it to the restaurant's owner; the lab spec doesn't ask for it.

### `PATCH /api/orders/{id}/status?status=X`

Manually update the order's status. Mostly a debugging aid; the canonical path for status changes is `DeliveryStatusUpdatedEvent` consumption.

Query param `status` (required) — one of the `OrderStatus` enum values.

Response — `200 OK`: updated `OrderResponse`.

Errors:
- `400` — unknown status value
- `404` — order doesn't exist

### `POST /api/orders/{id}/cancel`

Cancel an order. **Owner-only** — `order.customerUsername` must match `X-User-Username`. (ADMIN role can also cancel any order.)

Side effect: publishes `OrderCancelledEvent` to RabbitMQ → delivery-service marks the delivery `FAILED`.

Response — `200 OK`: updated `OrderResponse` with `status: "CANCELLED"`.

Errors:
- `400` — order is not in `PLACED` or `CONFIRMED` state (e.g. already shipped)
- `403` — caller doesn't own this order
- `404` — order doesn't exist

## Internal endpoints (not gateway-routed)

### `GET /api/internal/orders/{id}`

Used by **delivery-service**'s manual create-delivery endpoint (Phase 7 bridge — the event-driven path doesn't need this).

Response — `200 OK`:
```json
{
  "id": 1,
  "status": "PLACED",
  "customerId": 1,
  "customerName": "John Doe",
  "restaurantId": 1,
  "restaurantName": "Mama's Kitchen",
  "restaurantAddress": "45 Marina Road",
  "deliveryAddress": "123 Main St"
}
```

## Events emitted

| Event | Routing key | When |
|---|---|---|
| `OrderPlacedEvent` | `order.placed` | After a successful `POST /api/orders` (AFTER_COMMIT) |
| `OrderCancelledEvent` | `order.cancelled` | After a successful `POST /api/orders/{id}/cancel` (AFTER_COMMIT) |

## Events consumed

| Event | From queue | Effect |
|---|---|---|
| `DeliveryStatusUpdatedEvent` | `order.delivery-events` | Updates order status — `ASSIGNED` → `CONFIRMED`, `PICKED_UP`/`IN_TRANSIT` → `OUT_FOR_DELIVERY`, `DELIVERED` → `DELIVERED` |

## Error envelope

See [`README.md`](./README.md). Order Service maps:

| Exception | Status |
|---|---|
| `ResourceNotFoundException` | 404 |
| `UnauthorizedException` (ownership) | 403 |
| `IllegalStateException` (bad state transition) | 400 |
| `CustomerServiceUnavailableException` | 503 |
| `RestaurantServiceUnavailableException` | 503 |
