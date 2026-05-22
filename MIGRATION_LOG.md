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

---

## Phase 3 — Eureka service registry (2026-05-11)

### What changed

- `infrastructure/eureka-server/` — a tiny Spring Boot app on port `8761`. Three files (pom + main + application.yml) plus a README. `@EnableEurekaServer` is the only annotation that matters.
- Uses `spring-boot-starter-parent` as the Maven parent (Spring Boot's own parent — not a cross-service one) and imports the Spring Cloud BOM (`2023.0.3`) for managed Spring Cloud versions compatible with Spring Boot `3.3.0`.

### Why

- **Eureka first, services second.** Building the registry before any clients exist means service registration "just works" when Phase 4 starts — there's already something listening at `:8761`.
- **`register-with-eureka: false` / `fetch-registry: false`** — the server doesn't try to register with itself. Forgetting these is one of the most common Eureka misconfigurations; it spams the logs and burns CPU.
- **`enable-self-preservation: false` in local dev.** Self-preservation is a production safety net (prevents mass eviction during network partitions). Locally it makes stopped services linger in the dashboard, which causes confusion. We'll re-enable it in Docker / prod profiles.
- **No security on the Eureka dashboard.** Acceptable because the dashboard isn't exposed publicly in real prod (network policy). For the lab it's reachable so the grader can verify registration.

### Tradeoffs

- Single Eureka instance — not HA. Real prod runs at least two peers. Out of scope.
- `spring-boot-starter-parent` is used here (and will be in every Spring Boot app); this is Spring Boot's own parent, NOT a cross-service shared parent. The "no shared parent" rule still holds.

### Known limitations

- No Docker profile yet. Phase 10 will add `application-docker.yml` so services running in containers reach Eureka via the compose hostname `eureka-server` instead of `localhost`.

### Next phase unblocks

Customer Service extraction (Phase 4) — the first real microservice. It will register with Eureka, expose `/api/auth/**` + `/api/customers/**`, own `customer_db`, and become the source of truth for identity.

---

## Phase 5 — Restaurant Service extraction (2026-05-13)

> Branched from `main`. Customer Service (Phase 4) lives on its own branch; this branch does NOT touch customer-service code. A small follow-up commit on the Phase 4 branch will deduplicate `JwtAuthenticationFilter` once both branches are ready to merge.

### What changed

- **`shared/common-security/`** got `JwtAuthenticationFilter.java` — promoted from customer-service now that restaurant-service is a second caller. Same logic, no behavior change for callers.
- **`services/restaurant-service/`** on `:8082`, owns `restaurant_db`. Two-entity aggregate: `Restaurant` (root) + `MenuItem` (child) with a real FK inside the same DB.
- **First Feign client + Resilience4j circuit breaker.** `CustomerServiceClient` → `customer-service` for `promote-to-owner`. Fallback throws `CustomerServiceUnavailableException` → HTTP 503.
- **`/api/internal/restaurants/{id}/validate-order`** — the endpoint Order Service will call in Phase 6 to validate menu items and snapshot live prices.

### Why these decisions

- **`ownerId` + `ownerUsername` snapshot on `Restaurant`.** Storing only `ownerId` would force Restaurant Service to call Customer Service every time it checks ownership. Snapshotting the username at create-time means ownership checks are local. The tiny drift risk (user changes username) is acceptable; usernames are immutable in our customer model anyway.
- **Promotion via Feign, not events.** The lab spec implies inter-service REST for this. Async via events would arguably be cleaner (eventual consistency for role), but Phase 8 reserves events for delivery; keeping promotion synchronous shows Feign + circuit breaker explicitly.
- **The restaurant is saved BEFORE the promote-to-owner call.** If Customer Service is down, the user still gets their restaurant — the role bump just lags. Better than failing restaurant creation due to a flaky downstream. Documented in the README as "eventual consistency".
- **Fallback throws an exception instead of returning a stub.** A 503 with a clear message is more honest than pretending success. Lab spec calls for "graceful error handling when a dependent service is unavailable" — graceful here means *informative*, not *silent*.
- **`validateOrder` returns snapshot data including `restaurantName`, `unitPrice`, `menuItemName`.** Order Service writes those into `order_db` and never has to fan back out to Restaurant Service for reads. Historical orders stay immutable to upstream renames.
- **Filter promotion timing.** I extracted `JwtAuthenticationFilter` to common-security on the *second* caller, not the first. Premature extraction creates the wrong abstraction; real second use teaches you what's actually shareable.

### Tradeoffs

- The restaurant created → promote-to-owner failure scenario leaves the system in a temporarily inconsistent state (restaurant exists, owner still has `CUSTOMER` role). Acceptable for this project; production would queue a retry.
- `validate-order` endpoint is unauthenticated (`/api/internal/**`). Relies on network policy. Same trade-off as Phase 4.
- A user's JWT still says `CUSTOMER` after promotion until they log in again — JWTs don't auto-refresh. Standard limitation, documented.

### Known limitations

- No restaurant rating updates (the `rating` column exists but no endpoint writes it). Future work.
- No restaurant deactivation endpoint. Lab spec didn't ask for it.
- The `/api/internal/restaurants/{id}/validate-order` endpoint does not check if the restaurant is active. Order Service decides whether to reject inactive-restaurant orders.

### Cross-branch coordination

Two branches will both touch `MIGRATION_LOG.md`:
- `feat/customer-service-extraction` adds a Phase 4 entry between Phase 3 and the current "Next phase unblocks" line.
- `feat/restaurant-service-extraction` (this branch) adds the Phase 5 entry below that same line.

When both PRs merge, the resulting log should read Phase 0 → 1 → 2 → 3 → 4 → 5 in order. Trivial conflict, resolvable in the merge.

### Next phase unblocks

Order Service extraction (Phase 6). It calls Customer Service (`getById`) and Restaurant Service (`validateOrder`) via Feign. It uses the snapshot data from `validateOrder` to write `OrderItem` rows that include `menuItemName` and `unitPrice` — Order's reads will never fan out to Restaurant Service.

---

## Phase 6 — Order Service extraction with Feign + Resilience4j (2026-05-14)

### What changed

- **`services/order-service/`** on `:8083`, owns `order_db` (Order + OrderItem aggregate).
- **Two Feign clients**, each with its own Resilience4j circuit breaker and fallback:
  - `CustomerServiceClient.getById(id)` — 3s timeout, 503 fallback
  - `RestaurantServiceClient.validateOrder(id, items)` — 5s timeout, 503 fallback
- **Snapshot read model** is now fully real: `Order` stores `customerName`, `customerUsername`, `restaurantName`, `restaurantAddress`; `OrderItem` stores `menuItemName` + `unitPrice`. After save, reads never fan out.

### Why these decisions

- **No RabbitMQ in Phase 6.** Phase 8 owns the entire event integration. Putting half of it here would mean a stub publisher with no consumer — confusing for review. The synchronous `DeliveryService.createDeliveryForOrder()` from the monolith just doesn't happen yet; we accept that delivery records lag until Phase 8.
- **Feign calls happen BEFORE the local transaction opens.** They're read-only validations; treating them as part of the order persistence transaction would only make a long-running transaction even longer and increase lock contention. The order save itself is a single short local transaction.
- **`customerUsername` is snapshotted on `Order`.** Ownership checks (cancel, future endpoints) compare it against the JWT-derived username — no inter-service call required at check time.
- **Each downstream call has its own circuit breaker instance.** Sharing one breaker across services would mean Restaurant Service blips can open the breaker on Customer calls. Isolation per dependency is the standard practice.
- **Timeouts are tuned per call.** `getById` is cheap (3s). `validateOrder` does a DB lookup of menu items + price computation (5s). Generous for now; would tighten with real telemetry.
- **Fallback throws rather than returning a stub object.** Returning a fake `CustomerSummary` (or worse, `null`) would corrupt the order. Throwing → 503 is the only honest answer.
- **No driver/delivery embedded in `OrderResponse`.** Clients query Delivery Service directly for that. The Order's own `status` enum reflects the journey. This breaks the monolith's "one big DTO with everything" pattern intentionally.
- **`updateStatus` endpoint stays manual.** Phase 8 will drive it from `DeliveryStatusUpdatedEvent` consumption, but the manual endpoint is useful for testing and stays available during the transition.

### Tradeoffs

- **Order placement requires both Customer Service AND Restaurant Service up.** Two synchronous dependencies. Could be reduced (e.g., trust JWT for customer info entirely, skip the lookup), but the monolith's `placeOrder` already fetched both — preserving the contract.
- **Code duplication with restaurant-service for service-unavailable exceptions.** Both define their own `CustomerServiceUnavailableException`. Tolerated; extract to common-web on a third occurrence.
- **`updateStatus` and `cancel` don't yet propagate to Delivery.** Until Phase 8, an order can be `CANCELLED` while its delivery still says `ASSIGNED`. Documented; resolved by event flow in Phase 8.

### Known limitations

- No idempotency on `placeOrder` — a retried `POST` creates duplicate orders. A request ID + dedupe table would fix this; out of scope.
- No tax / promo code logic — total is plain sum of (price × quantity) + `2.99` delivery fee.
- `restaurant/{restaurantId}` endpoint returns orders without authorization check. Real prod would require the caller to be the restaurant's owner.

### Next phase unblocks

Delivery Service extraction (Phase 7). It will own `delivery_db`, expose driver-facing endpoints, and be ready to consume `OrderPlacedEvent` in Phase 8. Once Phase 7 ships, all four business services exist — Phase 8 wires them together asynchronously.

---

## Phase 8 — RabbitMQ events + DLQ (2026-05-16)

### What changed

The Order ↔ Delivery integration switches from "still missing" to event-driven.

**Order Service**
- Adds `spring-boot-starter-amqp` + `common-events` deps.
- `RabbitMqConfig` declares the shared topic exchange (`food-delivery.events`), DLX (`food-delivery.events.dlx`), the consumer queue `order.delivery-events` (bound to routing key `delivery.status-updated`), and its DLQ.
- `OrderEventPublisher` listens for `OrderPlacedEvent` / `OrderCancelledEvent` as Spring application events with `@TransactionalEventListener(AFTER_COMMIT)` and ships them via `RabbitTemplate`.
- `DeliveryStatusEventListener` consumes `DeliveryStatusUpdatedEvent` and advances order status (`PICKED_UP` → `OUT_FOR_DELIVERY`, `DELIVERED` → `DELIVERED`).
- `ProcessedEvent` + repository — idempotency ledger keyed on `eventId`.
- `OrderService.placeOrder` and `cancel` now `applicationEventPublisher.publishEvent(...)` after their writes.

**Delivery Service**
- Same dependencies added.
- `RabbitMqConfig` declares the same exchanges (idempotent on the broker), its consumer queue `delivery.order-events` bound to BOTH `order.placed` and `order.cancelled`, and its DLQ.
- `OrderEventListener` consumes both inbound events via `@RabbitHandler` per concrete type. Defers actual work to `DeliveryService.createFromOrderPlaced(event)` and `DeliveryService.markFailedForOrder(orderId)`.
- `DeliveryEventPublisher` listens for `DeliveryStatusUpdatedEvent` with `@TransactionalEventListener(AFTER_COMMIT)` and ships to RabbitMQ.
- `DeliveryService` gets two new methods (`createFromOrderPlaced`, `markFailedForOrder`) and now publishes `DeliveryStatusUpdatedEvent` on every status-changing path.
- `ProcessedEvent` + repository.

### Why these decisions

- **Same event records double as Spring application events AND AMQP payloads.** The records from `common-events` already exist; rather than introducing a separate "internal Spring event" type, we publish the public event via `ApplicationEventPublisher` and the listener catches it in the `AFTER_COMMIT` phase. One class, one shape, used in both places.
- **`@TransactionalEventListener(AFTER_COMMIT)` on publisher.** Guarantees the DB write is committed before the message hits RabbitMQ. Crashes between commit and publish still lose the event — that's the documented tradeoff. A transactional outbox would be the real fix; out of scope.
- **Idempotency table per service, not a shared one.** Each consumer's `processed_events_*` table lives in its own DB. Ownership matches the service boundary. Trade-off: small duplication of schema. Worth it for autonomy.
- **`@RabbitHandler` per concrete event type.** The Jackson converter sets `__TypeId__` on outbound messages; the consumer-side converter uses it to instantiate the correct record. We trust only the `com.fooddelivery.shared.events` package — anything else lands in the default handler and is DLQ'd with a clear log line.
- **`createFromOrderPlaced` does NOT call Order Service via Feign.** The `OrderPlacedEvent` payload already carries `restaurantAddress`, `deliveryAddress`, `customerName`. Eliminating the Feign call removes a runtime dependency from the consumer path — the event is fully self-sufficient. The Feign-based `createForOrder` stays as an admin / replay path.
- **DLQ wiring via `x-dead-letter-exchange` on the queue + DLX with same routing keys.** After 3 retry attempts (configured in `application.yml`) a failed message lands in the matching DLQ. Operators inspect manually; no auto-replay.
- **Duplicate `DuplicateResourceException` is swallowed in `OrderEventListener.onOrderPlaced`.** A delivery may already exist (e.g. from a previous successful run whose ack got lost). Recording the eventId as processed and moving on is more useful than churning retries into the DLQ.

### Tradeoffs

- **No transactional outbox.** Document-only mitigation — if the JVM dies between order commit and Rabbit publish, that single order's downstream effects are lost. Recovery is manual today (the `POST /api/internal/deliveries` admin endpoint).
- **Single consumer per queue.** Multiple consumers would mean a race on the idempotency check; the unique constraint backstops it but the work would briefly run twice. Tightening this needs proper "ack-after-commit" semantics — workable but out of scope for the lab.
- **Status mapping is one-way and lossy.** `DeliveryStatus.IN_TRANSIT` and `DeliveryStatus.PICKED_UP` both map to `OrderStatus.OUT_FOR_DELIVERY` — Order Service doesn't distinguish them. Acceptable for the customer-facing UX; if a driver app needs the precision, it queries Delivery Service directly.
- **The shared event JSON is the contract.** Renaming an event field becomes a breaking change. Treated like an API: backwards-compatible adds only.

### Known limitations

- No event replay tool. Failed messages sit in the DLQ until someone manually republishes them via the RabbitMQ management UI.
- No tracing — correlation IDs propagate via HTTP headers but not yet across RabbitMQ message properties. Easy to add later (set `correlation_id` on outbound, restore to MDC on inbound).
- The `processed_events_*` tables grow without bound. A nightly purge job (delete rows older than ~7 days) would handle the cleanup. Out of scope.

### Verifying end-to-end

Requires RabbitMQ running locally:

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
# Management UI: http://localhost:15672  (guest / guest)
```

Then start eureka + all four services, place an order via Order Service. Expected:
1. `POST /api/orders` returns 201 with order status `PLACED`.
2. Within a couple of seconds, `GET /api/deliveries/order/{orderId}` returns a delivery with status `ASSIGNED`.
3. `GET /api/orders/{id}` shows the order now in `CONFIRMED` (driven by the inbound `DeliveryStatusUpdatedEvent` from step 2).
4. `PATCH /api/deliveries/{id}/status?status=PICKED_UP` → order becomes `OUT_FOR_DELIVERY`.
5. `PATCH /api/deliveries/{id}/status?status=DELIVERED` → order becomes `DELIVERED`.

Stop RabbitMQ and place a new order: the order is saved (DB commit succeeds) but no delivery is created. Bring RabbitMQ back up — Spring AMQP reconnects but the lost event is not replayed (no outbox). That's the documented limitation; for the lab, just don't stop RabbitMQ mid-flow.

### Next phase unblocks

Spring Cloud Gateway (Phase 9). With all four business services event-integrated, the gateway becomes the public entry point — single endpoint for clients, JWT validation, route predicates per service via `lb://`, Redis-backed rate limiting on `POST /api/orders`, and circuit-breaker fallback routes that return clean JSON instead of timeouts.
