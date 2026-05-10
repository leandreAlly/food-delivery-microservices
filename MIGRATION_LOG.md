# Migration Log — Food Delivery Platform: Monolith → Microservices

> A per-phase decision journal. The goal of this document is not to repeat *what* was done (the git history does that) but to capture **why** each decision was made, the tradeoffs considered, and the limitations we accepted.

---

## Phase 0 — Discovery & target architecture (2026-05-10)

### Bounded contexts identified in the monolith

| Context | Aggregate root | Owns | Will only reference others by ID |
|---|---|---|---|
| Customer / Identity | `Customer` | username, email, password (BCrypt), profile, role | — |
| Restaurant Catalog | `Restaurant` + `MenuItem` (same aggregate) | catalog, pricing, availability, owner | `ownerId` |
| Order | `Order` + `OrderItem` (same aggregate) | order lifecycle, line items, totals | `customerId`, `restaurantId`, `menuItemId` |
| Delivery | `Delivery` | driver assignment, tracking, status | `orderId` |

### Coupling hotspots that must break (ranked by risk)

1. **`OrderService.placeOrder`** directly fetches `Customer` + `Restaurant` + `MenuItem` entities and synchronously calls `DeliveryService.createDeliveryForOrder` inside the same transaction.
2. **`RestaurantService.createRestaurant`** mutates `Customer.role` — a *write* across domain boundaries.
3. **`DeliveryService.updateStatus`** directly mutates `Order.status` — coupled state machine across two domains.
4. **`OrderResponse.fromEntity`** traverses Order → Customer + Restaurant + Delivery + MenuItem. Reads explode across four domains.
5. **JPA FKs** across domains: `Restaurant.owner`, `Order.customer`, `Order.restaurant`, `Order.delivery`, `OrderItem.menuItem`.

### Decisions

- **Strangler-fig migration**, not big-bang rewrite. Monolith stays referenceable under `monolith-reference/` throughout.
- **Standalone Maven projects** per service (no parent POM). Trades a small amount of version duplication for clearer learning and full isolation of each service.
- **PostgreSQL per service** — four physically separate containers in docker-compose, not one engine with four logical DBs. Stricter than necessary but matches the "real" microservices deployment shape.
- **JWT validated at the gateway only**. Downstream services trust `X-User-Id` / `X-User-Role` / `X-User-Username` headers and do not re-validate tokens. Network policy is the enforcement mechanism in real prod; for this project, documented as a constraint.
- **Snapshot read model** for Order — `customer_name`, `restaurant_name`, `menu_item_name`, `unit_price` are copied into `order_db` at order-placement time. Reads don't fan out; historical orders are immutable to upstream changes.
- **Async for delivery assignment, sync for menu validation.** Order placement *must* re-validate prices (sync Feign). Driver assignment does not need to be on the critical path (async RabbitMQ event).
- **No outbox pattern.** Events publish via `@TransactionalEventListener(phase = AFTER_COMMIT)`. Idempotency table per consumer + DLQ handle redelivery. Known limitation: a crash between commit and publish drops the event. Documented; out of scope to fix in this project.
- **No saga orchestrator.** Choreographed events with idempotent handlers.
- **No driver service.** Driver pool stays a hardcoded list (same as monolith). A real platform would split this out.

### Target architecture

```
Client → API Gateway (:8080, JWT + rate limit)
              ├─ lb://customer-service     :8081 → customer_db
              ├─ lb://restaurant-service   :8082 → restaurant_db
              ├─ lb://order-service        :8083 → order_db
              └─ lb://delivery-service     :8084 → delivery_db

All services register with Eureka (:8761).
Order ⇄ Restaurant: synchronous Feign + Resilience4j circuit breaker.
Order ⇄ Customer:   synchronous Feign + Resilience4j circuit breaker.
Order → Delivery:   async via RabbitMQ (order.placed, order.cancelled).
Delivery → Order:   async via RabbitMQ (delivery.status-updated).
```

### Risks accepted

| Risk | Mitigation |
|---|---|
| Lost transactional atomicity (order + delivery) | Idempotent consumers + DLQ. Documented limitation. |
| Stale menu prices | Order Service re-validates via Feign at placement; price snapshot taken after validation. |
| Event arrives before order commits | `@TransactionalEventListener(AFTER_COMMIT)` on publish side; idempotent dedupe on consume side. |
| Gateway is a single point of failure | Out of scope. Documented. Real prod runs N gateway replicas behind LB. |
| JWT secret rotation | Read from `JWT_SECRET` env var, not committed to YAML. Rotation = restart with new value. |

---

## Phase 1 — Workspace scaffold (2026-05-10)

### What changed

- Original monolith moved to `monolith-reference/` (read-only; not part of the build, not deployed).
- Workspace skeleton created — `services/`, `infrastructure/`, `shared/`, `docker/`, `docs/`, `postman/`, `scripts/`. All empty (`.gitkeep` placeholders).
- Root files added: `README.md`, `MIGRATION_LOG.md`, `.gitignore`, `.editorconfig`.

### Why

- Phase 1 is intentionally **structural only** — no Spring Boot code yet. A clean separation of "the workspace exists" from "services exist" keeps the commit history readable: if Phase 4 breaks, you can `git checkout` Phase 1 and see a clean, empty workspace.
- Moving the monolith aside (rather than deleting it) means we can:
  - Diff old vs. new code at any point during the migration.
  - Run the old monolith side-by-side with the new services to compare behavior.
  - Submit it as part of the deliverable so the grader sees both the starting point and the destination.

### Tradeoffs

- No parent POM means dependency versions will be duplicated across 6+ poms. Acceptable for a learning project; the explicitness is the point.
- Empty directories require `.gitkeep` files. Mildly ugly but standard.

### Known limitations

None — this phase changes no behavior.

### Next phase unblocks

Shared libraries (`common-security`, `common-events`, `common-web`) — Phase 2. These are tiny utility libs that the four services will depend on once they exist.

---

## Phase 2 — Shared libraries (2026-05-10)

### What changed

Three standalone Maven libraries under `shared/`:

- **`common-security`** — `JwtIssuer` / `JwtVerifier`, `SecurityHeaders` constants, `AuthenticatedUser` record, `HeaderAuthenticationFilter` (servlet filter that turns gateway-forwarded `X-User-*` headers into Spring `Authentication`), `@CurrentUser` annotation + argument resolver, `UnauthorizedException`.
- **`common-events`** — `DomainEvent` interface, record-based events (`OrderPlacedEvent`, `OrderCancelledEvent`, `DeliveryStatusUpdatedEvent`), `EventTopology` constants (exchange names, routing keys, queue names).
- **`common-web`** — `ErrorResponse` record (uniform error envelope), `CorrelationIdFilter` + constants (`X-Correlation-Id` → SLF4J MDC), common exceptions (`ResourceNotFoundException`, `DuplicateResourceException`), `AbstractGlobalExceptionHandler` base.

### Why

- **BOM import, not parent POM.** Each lib uses `spring-boot-dependencies` with `<scope>import</scope>` to inherit managed versions without bringing in plugin inheritance or the executable-jar `repackage` goal. Libraries should ship as plain jars, not Spring Boot fat jars.
- **No autoconfig.** Services will explicitly `@Bean` the filter and resolver. We trade boilerplate for transparency — every wired component is visible in the service's own config classes.
- **Records for events.** Events are value objects; records give immutability and a compact shape. `<parameters>true</parameters>` is set on the compiler so Jackson can deserialize records by parameter name.
- **`HeaderAuthenticationFilter`, not a JWT filter on services.** Downstream services don't validate JWTs — that's the gateway's job. They trust the gateway-forwarded `X-User-Id` / `X-User-Username` / `X-User-Role` headers. One trust boundary, one place to rotate the secret.
- **Idempotency built into the event contract.** Every event carries an `eventId` (UUID); consumers will record processed IDs in a small DB table to handle RabbitMQ at-least-once delivery.

### Tradeoffs

- Services must `mvn install` each shared lib before building. Mildly annoying for first-time setup; a `scripts/build-shared.sh` will arrive in Phase 10.
- No autoconfig means a couple of `@Bean` lines per service. Worth the visibility.

### Known limitations

- Only HS256 signing supported. Production should use RS256 + a JWKS endpoint; out of scope.
- `processed_events_*` table is per-service rather than a generic library — kept local to each consumer because the schema is tiny and per-service ownership is the point.

### Next phase unblocks

Eureka service registry (Phase 3) — a standalone Spring Boot app on `:8761`. Once it's up, the four services have somewhere to register, and the gateway has somewhere to discover services by logical name (`lb://customer-service`).
