# order-service

Order microservice. Owns `order_db` (Order + OrderItem aggregate). The runtime center of the system: every order placement is a two-Feign-call dance, and the snapshot-into-`order_db` pattern means subsequent reads never fan back out.

| | |
|---|---|
| Port | `8083` |
| Eureka name | `order-service` |
| Database | `order_db` (H2 in dev, PostgreSQL in Docker) |
| Depends on | Eureka (`:8761`), Customer Service (`/api/internal/customers/{id}`), Restaurant Service (`/api/internal/restaurants/{id}/validate-order`) |
| Used by | Delivery Service (Phase 7, via `/api/internal/orders/{id}`) |

## Run locally

```bash
# 1. Shared libs (only on first run)
(cd ../../shared/common-security && mvn -q install)
(cd ../../shared/common-web      && mvn -q install)

# 2. Eureka (separate terminal)
(cd ../../infrastructure/eureka-server && mvn spring-boot:run)

# 3. Customer Service (separate terminal)
(cd ../customer-service && mvn spring-boot:run)

# 4. Restaurant Service (separate terminal)
(cd ../restaurant-service && mvn spring-boot:run)

# 5. This service
mvn spring-boot:run
```

## Endpoints

### Authenticated (Bearer token)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/orders` | Place order — validates via Feign, snapshots data |
| `GET` | `/api/orders/{id}` | Get order by ID |
| `GET` | `/api/orders/my-orders` | Caller's order history |
| `GET` | `/api/orders/restaurant/{restaurantId}` | Orders for a restaurant |
| `PATCH` | `/api/orders/{id}/status?status=X` | Manual status update (will be event-driven in Phase 8) |
| `POST` | `/api/orders/{id}/cancel` | Cancel (owner only, PLACED or CONFIRMED status) |

### Internal (inter-service only — never routed by the gateway)

| Method | Path | Caller | Purpose |
|---|---|---|---|
| `GET` | `/api/internal/orders/{id}` | Delivery Service | Minimal order info incl. snapshot addresses + customer name |

## How `POST /api/orders` actually works

```
caller (JWT)
   ▼
[Order Service]
   │  1. GET /api/internal/customers/{caller.id}      ──► Customer Service
   │      └── 503 if down (CB fallback)               (Resilience4j CB)
   │
   │  2. POST /api/internal/restaurants/{rid}/validate-order ──► Restaurant Service
   │      └── 503 if down (CB fallback)               (Resilience4j CB)
   │      └── 400 if any item unavailable or restaurant inactive
   │
   │  3. Build Order + OrderItems with SNAPSHOT data:
   │      • customerName (from Customer Service)
   │      • restaurantName, restaurantAddress (from Restaurant Service)
   │      • menuItemName + unitPrice on each line (from validate-order)
   │
   │  4. Persist within a single local transaction.
   │
   │  Phase 8 will publish OrderPlacedEvent here → Delivery Service.
   ▼
201 Created  (OrderResponse with all snapshot fields populated)
```

After the order is saved, the Order Service **never** calls Customer or Restaurant for read paths. Renaming a dish on Restaurant Service or a customer changing their name in Customer Service does NOT retroactively change a historical order — that's the read-model snapshotting payoff.

## Circuit breaker observability

```
http://localhost:8083/actuator/health
http://localhost:8083/actuator/circuitbreakers
http://localhost:8083/actuator/circuitbreakerevents
```

Each Feign client has its own breaker:
- `CustomerServiceClient` — 3s timeout, opens at 50% failure rate over a 10-call window
- `RestaurantServiceClient` — 5s timeout (validate-order is heavier), same opening rule

## Quick smoke test

```bash
# Assuming customer-service is up and you registered + logged in:
TOKEN="<paste JWT>"

# Place an order (restaurant 1, menu items 1 and 2 must exist in restaurant-service)
curl -X POST http://localhost:8083/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "restaurantId": 1,
    "items": [
      {"menuItemId": 1, "quantity": 2},
      {"menuItemId": 2, "quantity": 1}
    ],
    "specialInstructions": "Extra pepper please"
  }'

# Fetch own orders
curl -H "Authorization: Bearer $TOKEN" http://localhost:8083/api/orders/my-orders

# Try cancel (must be PLACED or CONFIRMED)
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8083/api/orders/1/cancel

# Test the circuit breaker: stop customer-service, place an order
# Expect: 503 Service Unavailable with a clear message.
```

## What's intentionally NOT here yet

- **No delivery creation.** The monolith called `DeliveryService.createDeliveryForOrder()` synchronously inside `placeOrder`. We removed that — Phase 8 will publish `OrderPlacedEvent` and Delivery Service will consume it asynchronously.
- **No `OrderCancelledEvent` publish.** Same story — Phase 8.
- **`updateStatus` is a manual endpoint.** In Phase 8 it'll be driven by `DeliveryStatusUpdatedEvent` consumption. The manual endpoint stays during the transition for debugging.
- **No driver / delivery info on `OrderResponse`.** Clients call Delivery Service directly (once it exists) for that. The Order's own `status` (`OUT_FOR_DELIVERY`, `DELIVERED`) reflects the journey.

## Notes for the migration

- `Order.customerId` / `Order.restaurantId` / `OrderItem.menuItemId` are plain `Long` columns. No FK to other databases.
- Snapshot fields: `customerName`, `customerUsername`, `restaurantName`, `restaurantAddress`, `menuItemName`, `unitPrice`. The `customerUsername` snapshot is what we compare against `caller.username()` on cancel — no Feign call needed.
- The local transaction wraps the order save only. The two Feign calls happen *before* the transaction starts (read-only validation). If either Feign call fails after a partial success in some hypothetical longer flow, the transaction rolls back the order write cleanly.
