# Gateway API contract

The API Gateway has no business endpoints of its own. Its job is to route, validate JWTs, rate-limit, and forward fallback responses.

| | |
|---|---|
| Port | `8080` (this is what clients hit) |
| Eureka name | `api-gateway` |
| Stack | Reactive (Spring WebFlux + Netty) |

## Routing table

What the gateway forwards where.

| Method + Path | Route ID | Destination | Filters |
|---|---|---|---|
| `*` `/api/auth/**` | `customer-service` | `lb://customer-service` | CircuitBreaker |
| `*` `/api/customers/**` | `customer-service` | `lb://customer-service` | CircuitBreaker |
| `*` `/api/restaurants/**` | `restaurant-service` | `lb://restaurant-service` | CircuitBreaker |
| `POST` `/api/orders` | `order-service-place` | `lb://order-service` | RateLimit → CircuitBreaker |
| `*` `/api/orders/**` (everything else) | `order-service` | `lb://order-service` | CircuitBreaker |
| `*` `/api/deliveries/**` | `delivery-service` | `lb://delivery-service` | CircuitBreaker |
| `*` `/api/internal/**` | **(no route)** | — | Gateway returns `404` |

## Authentication behavior

Every request that's not on a public path goes through `JwtAuthGlobalFilter`:

1. Read `Authorization: Bearer <token>` header.
2. Verify the JWT with `JwtVerifier` (`app.jwt.secret`).
3. On success, mutate the request to add:
   - `X-User-Id` — numeric ID from the `uid` claim
   - `X-User-Username` — the `sub` claim
   - `X-User-Role` — the `role` claim
4. On failure, return `401` with the standard `ErrorResponse` envelope.

**Public paths that bypass the filter:**
- `/api/auth/**`
- `GET /api/restaurants/search/**`
- `GET /api/restaurants/*/menu`
- `/actuator/**`

## Rate limiting

Applied **only** to `POST /api/orders`. Token bucket backed by Redis:

| Setting | Value |
|---|---|
| `replenishRate` | 1 token/sec |
| `burstCapacity` | 3 |
| `requestedTokens` | 1 per call |
| Bucket key | `X-User-Id` (per-user) |

When the bucket empties, the gateway returns `429 Too Many Requests` with headers:

```
X-RateLimit-Remaining: 0
X-RateLimit-Burst-Capacity: 3
X-RateLimit-Replenish-Rate: 1
X-RateLimit-Requested-Tokens: 1
```

Default values are demo-friendly; production would tune them up.

## Circuit breakers (one per route)

Every route is wrapped in a Resilience4j circuit breaker that forwards to `/fallback/{service-id}` when open. Default config:

| Setting | Value |
|---|---|
| `sliding-window-size` | 10 |
| `minimum-number-of-calls` | 5 |
| `failure-rate-threshold` | 50% |
| `wait-duration-in-open-state` | 10s |
| `permitted-number-of-calls-in-half-open-state` | 3 |

Fallback response (HTTP `503`):

```json
{
  "timestamp": "2026-05-20T12:00:00Z",
  "status": 503,
  "error": "Service Unavailable",
  "message": "Customer Service is currently unavailable. Please try again shortly.",
  "path": "/fallback/customer-service",
  "fieldErrors": null
}
```

## Actuator endpoints

All on the gateway itself, no authentication.

| Path | Returns |
|---|---|
| `GET /actuator/health` | `{"status":"UP", ...}` — also includes circuit-breaker health |
| `GET /actuator/info` | Build info if configured |
| `GET /actuator/gateway/routes` | JSON array of all active routes + their filters |
| `GET /actuator/circuitbreakers` | State of every named breaker (`OPEN` / `CLOSED` / `HALF_OPEN`) |
| `GET /actuator/circuitbreakerevents` | Recent state-transition events |

## Error responses produced by the gateway directly

| Status | When |
|---|---|
| `401` | JWT missing or invalid on a protected path |
| `404` | Path doesn't match any route (e.g. `/api/internal/**`, typos) |
| `429` | `POST /api/orders` rate-limit bucket empty for this user |
| `503` | Downstream service circuit-broken or unreachable |
| `502` | Downstream service returned malformed response (rare) |
