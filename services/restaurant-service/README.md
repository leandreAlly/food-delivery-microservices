# restaurant-service

Restaurant catalog microservice. Owns `restaurant_db` (Restaurant + MenuItem aggregate). First service to make a cross-service Feign call (to Customer Service) and the first to use a Resilience4j circuit breaker.

| | |
|---|---|
| Port | `8082` |
| Eureka name | `restaurant-service` |
| Database | `restaurant_db` (H2 in dev, PostgreSQL in Docker) |
| Depends on | Eureka (`:8761`), Customer Service (for role promotion only) |
| Used by | Order Service (via `/api/internal/restaurants/{id}/validate-order`) |

## Run locally

```bash
# 1. Install shared libs (if not already done)
(cd ../../shared/common-security && mvn -q install)
(cd ../../shared/common-web      && mvn -q install)

# 2. Start Eureka (in another terminal)
(cd ../../infrastructure/eureka-server && mvn spring-boot:run)

# 3. Start Customer Service (in another terminal) — required for the
#    promote-to-owner Feign call during POST /api/restaurants.
#    You can still start Restaurant Service without it; just the create
#    flow will return 503 from the circuit breaker fallback.
(cd ../customer-service && mvn spring-boot:run)

# 4. Start this service
mvn spring-boot:run
```

## Endpoints

### Public

| Method | Path | Returns |
|---|---|---|
| `GET` | `/api/restaurants/search/all` | All active restaurants |
| `GET` | `/api/restaurants/search/city/{city}` | Filtered by city |
| `GET` | `/api/restaurants/search/cuisine/{type}` | Filtered by cuisine |
| `GET` | `/api/restaurants/{id}/menu` | Restaurant menu (available items only) |

### Authenticated (Bearer token)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/restaurants/{id}` | Any authenticated user |
| `POST` | `/api/restaurants` | Creates restaurant + **Feign-calls Customer Service to promote caller to RESTAURANT_OWNER** |
| `POST` | `/api/restaurants/{id}/menu` | Owner only |
| `PUT` | `/api/restaurants/menu/{itemId}` | Owner only |
| `PATCH` | `/api/restaurants/menu/{itemId}/toggle` | Owner only |

### Internal (inter-service only — never routed by the gateway)

| Method | Path | Caller | Purpose |
|---|---|---|---|
| `GET` | `/api/internal/restaurants/{id}` | Order Service | Minimal restaurant info |
| `POST` | `/api/internal/restaurants/{id}/validate-order` | Order Service | Validate menu items + return prices for snapshotting |

## Cross-service call

Restaurant Service has **one** Feign client: `CustomerServiceClient`, which calls Customer Service's `/api/internal/customers/{username}/promote-to-owner`. Wrapped in a Resilience4j circuit breaker — if Customer Service is unreachable, the fallback throws `CustomerServiceUnavailableException` and the API returns **HTTP 503**.

Circuit breaker state is visible at:
- http://localhost:8082/actuator/circuitbreakers
- http://localhost:8082/actuator/circuitbreakerevents

## Known quirk: stale JWTs after promotion

When a `CUSTOMER` creates their first restaurant, Customer Service promotes them to `RESTAURANT_OWNER`. But **the JWT they're holding still says `CUSTOMER`** — JWTs are self-contained and don't auto-refresh. They have to log in again to get a token reflecting the new role.

Standard JWT behavior. Fixable with refresh tokens or token introspection — out of scope.

## Quick smoke test

```bash
# Get a JWT from customer-service first (see customer-service/README.md)
TOKEN="<paste JWT here>"

# Create a restaurant
curl -X POST http://localhost:8082/api/restaurants \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Mamas Kitchen",
    "description": "Traditional Nigerian cuisine",
    "cuisineType": "Nigerian",
    "address": "45 Marina Road",
    "city": "Lagos",
    "phone": "+234-1-555-0200",
    "estimatedDeliveryMinutes": 35
  }'

# Add a menu item
curl -X POST http://localhost:8082/api/restaurants/1/menu \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Jollof Rice", "description": "Smoky party-style", "price": 2500.00, "category": "Main"}'

# Browse public menu (no auth needed)
curl http://localhost:8082/api/restaurants/1/menu

# Inter-service validate-order (this is what Order Service will call)
curl -X POST http://localhost:8082/api/internal/restaurants/1/validate-order \
  -H "Content-Type: application/json" \
  -d '{"items": [{"menuItemId": 1, "quantity": 2}]}'
```

## Notes for the migration

- The monolith's `Restaurant.owner` (`@ManyToOne Customer`) is replaced with `ownerId: Long` + `ownerUsername: String` — no JPA reference to a Customer entity.
- Ownership checks use the snapshotted `ownerUsername` against the JWT-derived username. No call to Customer Service needed at check time.
- Restaurant Service does NOT validate that the `ownerId` actually exists in Customer Service. The JWT proves the user is authenticated; we trust that.
- The promote-to-owner flow is decoupled from restaurant creation — even if it fails (503), the restaurant is already saved. Eventual consistency: the user re-logs-in later or an admin can re-trigger.
