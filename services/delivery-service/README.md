# delivery-service

Delivery microservice. Owns `delivery_db`. Phase 7 builds the synchronous parts; Phase 8 wires it to RabbitMQ.

| | |
|---|---|
| Port | `8084` |
| Eureka name | `delivery-service` |
| Database | `delivery_db` (H2 in dev, PostgreSQL in Docker) |
| Depends on | Eureka (`:8761`), Order Service (`/api/internal/orders/{id}` at create time) |
| Will (Phase 8) consume | `OrderPlacedEvent`, `OrderCancelledEvent` |
| Will (Phase 8) publish | `DeliveryStatusUpdatedEvent` |

## Run locally

```bash
# 1. Shared libs
(cd ../../shared/common-security && mvn -q install)
(cd ../../shared/common-web      && mvn -q install)

# 2. Eureka (separate terminal)
(cd ../../infrastructure/eureka-server && mvn spring-boot:run)

# 3. Order Service (separate terminal — needed for the create-delivery Feign call)
(cd ../order-service && mvn spring-boot:run)

# 4. This service
mvn spring-boot:run
```

## Endpoints

### Authenticated (Bearer token)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/deliveries/{id}` | Get delivery |
| `GET` | `/api/deliveries/order/{orderId}` | Find delivery for a given order |
| `GET` | `/api/deliveries/status/{status}` | List by status (PENDING, ASSIGNED, PICKED_UP, etc.) |
| `PATCH` | `/api/deliveries/{id}/status?status=X` | Driver status progression |

### Internal (inter-service / admin only)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/internal/deliveries` | Manually create a delivery for an `orderId` — **Phase 7 bridge**, replaced by `OrderPlacedEvent` consumption in Phase 8 |

## Phase 7 → Phase 8 transition

Right now there's no RabbitMQ. To exercise the full flow today:

```bash
# 1. Place an order via Order Service → returns orderId (e.g. 1)
TOKEN="<paste JWT>"
ORDER_ID=$(curl -sX POST http://localhost:8083/api/orders \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"restaurantId":1,"items":[{"menuItemId":1,"quantity":1}]}' | jq -r .id)

# 2. Manually trigger delivery creation (Phase 8 will do this from an event)
curl -X POST http://localhost:8084/api/internal/deliveries \
  -H "Content-Type: application/json" \
  -d "{\"orderId\": $ORDER_ID}"

# 3. Look up the delivery
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8084/api/deliveries/order/$ORDER_ID

# 4. Simulate driver flow
curl -X PATCH -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8084/api/deliveries/1/status?status=PICKED_UP"

curl -X PATCH -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8084/api/deliveries/1/status?status=DELIVERED"
```

In Phase 8, step 2 disappears — placing the order auto-creates the delivery via `OrderPlacedEvent`, and step 4's status update auto-syncs back to the Order via `DeliveryStatusUpdatedEvent`.

## Why a Feign call is needed at create time

Delivery Service has no way to know the `restaurantAddress`, `deliveryAddress`, or `customerName` for an order — those live in `order_db`. Rather than make Delivery's `POST` request specify them all (and trust the caller), Delivery looks them up from Order Service itself via `GET /api/internal/orders/{id}` and snapshots the result into `delivery_db`.

In Phase 8 the same data arrives **inside the event payload** (`OrderPlacedEvent` carries all these fields), so the Feign call goes away — the event is self-sufficient. The Feign client stays in the code for the `internal create` endpoint (which itself becomes optional admin-only at that point).

## Notes for the migration

- `Delivery.orderId` has a unique index — there is exactly one delivery per order.
- Snapshot fields (`customerName`, `pickupAddress`, `deliveryAddress`) ensure Delivery never calls Order/Customer/Restaurant on read paths.
- Status update endpoint mutates `delivery_db` only. The Order's status will lag (still `PLACED`) until Phase 8 adds `DeliveryStatusUpdatedEvent`. Document for testers.
- Driver pool is hardcoded — same as the monolith. A real platform would have a Driver Service.
