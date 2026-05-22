# api-gateway

Spring Cloud Gateway — the single public entry point for the platform.

| | |
|---|---|
| Port | `8080` |
| Eureka name | `api-gateway` |
| Stack | Reactive (Spring WebFlux + Netty) — **not** servlet |
| Depends on | Eureka `:8761`, Redis `:6379` |
| Used by | Every client. Internal `/api/internal/**` paths are deliberately not routed. |

## What this gateway does

| Concern | Implementation |
|---|---|
| Single entry point | Clients only know `:8080`. All four services sit behind `lb://` URIs resolved through Eureka. |
| JWT validation | `JwtAuthGlobalFilter` validates the Bearer token once and forwards `X-User-Id` / `X-User-Username` / `X-User-Role` headers downstream. |
| Public-path bypass | `/api/auth/**`, `GET /api/restaurants/search/**`, `GET /api/restaurants/*/menu`, and `/actuator/**` skip the JWT check. |
| Rate limiting | Redis-backed token bucket on `POST /api/orders` only. Bucketed per user via `X-User-Id`. |
| Circuit breakers | Resilience4j wraps every route. When a downstream is down, the route forwards to `FallbackController` which returns a uniform 503 envelope. |
| Routing | Spring Cloud Gateway with `lb://` URIs — load-balanced, no hardcoded URLs. |

## Routes

| Method + Path | → | Service | Filters |
|---|---|---|---|
| `*` `/api/auth/**` | → | customer-service | CircuitBreaker |
| `*` `/api/customers/**` | → | customer-service | CircuitBreaker |
| `*` `/api/restaurants/**` | → | restaurant-service | CircuitBreaker |
| `POST` `/api/orders` | → | order-service | RateLimit + CircuitBreaker |
| `*` `/api/orders/**` | → | order-service | CircuitBreaker |
| `*` `/api/deliveries/**` | → | delivery-service | CircuitBreaker |
| `*` `/api/internal/**` | — | — | **Not routed → 404** |

## Trust chain

```
Client ──Bearer JWT──▶ Gateway ──verifies via JwtVerifier──▶ injects X-User-* ──▶ Service
                                                                                   │
                                                                       JwtAuthenticationFilter
                                                                       (also still validates the
                                                                       Bearer token — defense in
                                                                       depth in case anyone tries
                                                                       to bypass the gateway)
```

## Prerequisites to run

| Service | How |
|---|---|
| Eureka `:8761` | `(cd ../eureka-server && mvn spring-boot:run)` |
| Redis `:6379` | `docker run -d --name redis -p 6379:6379 redis:7-alpine` |
| RabbitMQ `:5672` | `docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management` |
| Four services | `(cd ../../services/<name> && mvn spring-boot:run)` for each |

## Run

```bash
mvn spring-boot:run
```

Verify:
- http://localhost:8761 — `API-GATEWAY` shows up alongside the four services
- http://localhost:8080/actuator/health → `{"status":"UP"}` (includes circuit-breaker health)
- http://localhost:8080/actuator/gateway/routes → JSON listing of all 6 routes with their filters
- http://localhost:8080/actuator/circuitbreakers → state of each named breaker

## Smoke tests

### JWT validation works

```bash
# Public — no token needed
curl -i http://localhost:8080/api/restaurants/search/all

# Protected without token → 401 (from the gateway, not the service)
curl -i http://localhost:8080/api/customers/me

# Login through the gateway (proxies to customer-service)
TOKEN=$(curl -sX POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"password123"}' | jq -r .token)

# Protected with token → 200, and the downstream service sees the
# X-User-* headers injected by the gateway
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/customers/me | jq
```

### Rate limit works

```bash
# Demo limits: replenishRate=1, burstCapacity=3
for i in $(seq 1 20); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST http://localhost:8080/api/orders \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"restaurantId":1,"items":[{"menuItemId":1,"quantity":1}]}')
  echo "$i: HTTP $STATUS"
done

# Expect: ~3 x 201, then mostly 429 with occasional 201 (1 token/sec drip-feed)
```

### Circuit breaker fires when a service goes down

```bash
# Stop customer-service (Ctrl+C its terminal)

# Now hit /api/auth/login — first ~5 requests reach customer-service,
# fail, the breaker opens, subsequent requests get 503 from the fallback.
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"password123"}'

# Inspect breaker state
curl -s http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers'
# Look for customerServiceCircuitBreaker → "state": "OPEN"
```

## Tunable knobs

| Property | Default | Override |
|---|---|---|
| `app.jwt.secret` | dev placeholder | `JWT_SECRET` env var |
| Rate limit replenish rate | `1` token/sec | `application.yml` |
| Rate limit burst | `3` tokens | `application.yml` |
| Circuit-breaker failure-rate threshold | `50` % | `resilience4j.circuitbreaker.configs.default.failure-rate-threshold` |
| Circuit-breaker open duration | `10s` | `resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state` |
| Redis host | `localhost` (dev), `redis` (docker) | `REDIS_HOST` |

## What this gateway is NOT

- **Not the place for business logic.** Transformations / aggregations belong in services or a BFF.
- **Not a database client.** It owns no schema.
- **Not highly available.** Single instance for the lab; production would run N replicas behind a load balancer. Documented as a known limitation in `MIGRATION_LOG.md`.
