# Migration decisions — categorical reference

Every architectural choice in one place, grouped by topic. Use this when you want to know **why** something is the way it is. For chronological per-phase reasoning, see [`MIGRATION_LOG.md`](../MIGRATION_LOG.md). For diagrams and component inventory, see [`docs/ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## 1. Service boundaries & data ownership

### Four services: Customer, Restaurant, Order, Delivery

- **Why**: The monolith's package structure already revealed these four bounded contexts. Each has its own write model, its own consumers, and its own scaling axis (Customer is read-heavy, Order is write-heavy, Delivery is event-driven). Splitting at any finer granularity would create chatty calls; merging any two would re-create the coupling we set out to break.
- **Tradeoff**: We didn't extract a Driver Service even though the monolith treats drivers as a domain concern. The driver pool stays a hardcoded list, same as the monolith. A real platform would split this out.

### Database-per-service — four physically separate PostgreSQL containers

- **Why**: Database is the strongest coupling point. If two services share a schema, they're effectively one. Four containers (rather than one Postgres engine with four logical DBs) is stricter than needed but matches a real production deployment shape and forces ID-only references across boundaries.
- **Tradeoff**: 4× the RAM footprint at dev time. Fine on a dev box; in prod each would be its own managed instance anyway.

### Snapshot read model on Order and Delivery

- **Why**: An order from six months ago should not change if the restaurant later renames a dish or the customer updates their address. `Order` and `OrderItem` store `customerName`, `restaurantName`, `menuItemName`, `unitPrice` at write time — copied from upstream services via Feign during placement. After save, reads never fan out.
- **Tradeoff**: Snapshot data is fixed at write time. If a customer legitimately wants to update their old orders' delivery address (e.g. moved house), they can't — but that's actually correct: historical record. Stale catalog data (price changes) is an explicit upside.

### `/api/internal/**` prefix for inter-service-only endpoints

- **Why**: Services need lookups that bypass JWT auth (Feign clients call them from inside the trusted network). Putting them on a distinct path prefix means the gateway has no route for them — they're literally unreachable from outside without changing the routing config.
- **Tradeoff**: Relies on network policy. If someone bypasses the gateway and reaches a service directly, `/api/internal/**` is wide open. Documented; production would mTLS the internal traffic.

---

## 2. Synchronous vs asynchronous communication

### Sync (Feign + Resilience4j) for: customer lookup, menu validation, role promotion

- **Why**: These calls happen on the request path and need an answer before the response is sent. Order placement *must* validate live menu prices; restaurant creation *must* promote the user to RESTAURANT_OWNER before returning.
- **Tradeoff**: Synchronous coupling — Order Service can't place an order if Customer Service or Restaurant Service is down. Resilience4j circuit breakers + 503 fallbacks make the failure mode obvious rather than hanging.

### Async (RabbitMQ topic exchange) for: delivery creation, status round-trip

- **Why**: Delivery assignment doesn't need to happen on the order-placement critical path. The client should get their order receipt immediately; driver assignment can complete a few hundred milliseconds later. Same in reverse: when a driver picks up an order, Order Service shouldn't block on a synchronous notification.
- **Tradeoff**: Eventual consistency. A client polling `GET /api/deliveries/order/{id}` immediately after `POST /api/orders` may get `404` until the event consumer catches up. Documented in the Postman collection's timing note.

### Publish-after-commit (no outbox table)

- **Why**: Spring's `@TransactionalEventListener(AFTER_COMMIT)` ensures the DB commit succeeds before the broker sees the event. The shared event POJO from `common-events` is published via `ApplicationEventPublisher`; the listener catches it post-commit and ships it via `RabbitTemplate`.
- **Tradeoff**: If the JVM dies between commit and publish, the event is lost. A transactional outbox would fix it with an extra table + relay; out of scope. Documented as the known limitation; manual recovery via the `POST /api/internal/deliveries` admin endpoint.

### Idempotent consumers via per-service `processed_events_*` tables

- **Why**: RabbitMQ delivers at-least-once. Each consumer inserts `(eventId, eventType, processedAt)` into its own table within the same transaction as the downstream work. A duplicate redelivery hits the PK constraint, rolls back, and is treated as a no-op.
- **Tradeoff**: The table grows unbounded. A nightly purge of rows older than 7 days is the obvious cleanup; not implemented.

---

## 3. Security

### JWT validated at the gateway AND at each service (defense in depth)

- **Why**: The lab spec calls for "JWT auth at the gateway level". We do that — `JwtAuthGlobalFilter` validates the token and forwards `X-User-*` headers. But each downstream service *also* runs `JwtAuthenticationFilter` from `common-security` on its own. If anyone bypasses the gateway (network misconfig, debug port exposure, mTLS lapse), the service is not wide open.
- **Tradeoff**: Slight duplicated work — two HMAC verifications per request. Negligible cost for the additional safety.

### HS256 with a shared secret (not RS256 + JWKS)

- **Why**: Single secret, propagated to every verifier via env. Simple enough for a learning project where the focus is the migration, not key rotation.
- **Tradeoff**: Compromised secret = full token forgery across all services. RS256 with key rotation via JWKS is the production-grade answer; documented as future work.

### `JsonAuthenticationEntryPoint` + `JsonAccessDeniedHandler` in `common-security`

- **Why**: Spring Security 6's default routes anonymous-user denials through the `AccessDeniedHandler`, returning `403`. Semantically wrong — "you have no credentials" is `401`. These two shared classes detect the anonymous case and downgrade to `401` while keeping `403` for real role denials.
- **Tradeoff**: Each service has to wire them in `SecurityConfig` (two lines). The alternative — relying on Spring's default — produces misleading error codes that confuse clients.

### Role stored in the JWT claim — no remote lookup

- **Why**: After login, the role is encoded in the token. Every downstream service can authorize role checks locally without a Feign call to Customer Service.
- **Tradeoff**: When a customer's role changes (CUSTOMER → RESTAURANT_OWNER after creating their first restaurant), their existing token is stale until they log in again. Standard JWT limitation; refresh tokens or token introspection would fix it.

---

## 4. Resilience

### Two layers of circuit breakers (Feign-level + Gateway-level)

- **Why**: They protect against different failures. The Feign CB protects a *service* from a flaky downstream (Order Service from a slow Customer Service). The Gateway CB protects the *client* from a service that's completely down or its Feign fallback is also broken.
- **Tradeoff**: Two configs to keep in sync. Both inherit from a shared default — tuning is one block, not five.

### Rate limiting on `POST /api/orders` only

- **Why**: Reads aren't the abuse vector. Order placement is — it triggers DB writes and downstream sync calls. Limiting reads would penalize legitimate browse behavior. The single rate-limited route is split out as `order-service-place` with its own filter; the catch-all `/api/orders/**` is unlimited.
- **Tradeoff**: Other endpoints could in theory be abused (e.g. a malicious caller hammering `GET /api/orders/my-orders`). For the lab, single-endpoint limiting is enough; production would expand to other write paths.

### Bucket key = `X-User-Id`, not IP

- **Why**: Bucketing per IP is fragile (NAT, mobile carriers, corporate proxies). Per-user matches the actual usage pattern. The JWT filter runs before the rate limiter, so the header is guaranteed present.
- **Tradeoff**: Doesn't protect against credential-stuffing attacks (each attacker uses a different account). Multi-layer defense (IP + user) is the production answer.

### Healthchecks + `depends_on: condition: service_healthy` in docker-compose

- **Why**: Without healthcheck-based ordering, services start before their dependencies (Eureka, Postgres, RabbitMQ) and crash-loop. Explicit healthchecks make boot order deterministic — DBs and infra first, then Eureka, then services in dependency order, then the gateway.
- **Tradeoff**: First cold-start takes ~2 minutes for the whole stack to be healthy. Subsequent restarts are faster.

---

## 5. Build & runtime structure

### Standalone Maven projects per service, no shared parent POM

- **Why**: Locked decision early. Each service's `pom.xml` is self-contained — every dependency version visible in the file that needs it. No magic inheritance. Pedagogically clearer; matches how most real microservices repos look when split across repos.
- **Tradeoff**: Spring Boot version duplicated across 6 poms. Bumping Spring Boot means editing 6 files. Acceptable for project scale; an aggregator POM would centralize this without re-introducing inheritance.

### Multi-stage Dockerfiles, build context = repo root

- **Why**: Each service needs both its own source *and* the shared libs. Setting the build context to the root means one `COPY shared/common-* …` works. Stage 1 brings Maven + JDK; stage 2 keeps only JRE + jar. Final images drop from ~600 MB to ~150 MB.
- **Tradeoff**: Changes anywhere in the repo can invalidate Docker layer caching. `.dockerignore` mitigates by excluding noise (target, .git, monolith-reference, docs).

### Reactive gateway (Spring Cloud Gateway / WebFlux), servlet for everything else

- **Why**: Spring Cloud Gateway is built on Netty/WebFlux — that's how it scales to many concurrent connections per JVM. Services don't have the same need, and mixing servlet + reactive in one app is a known footgun.
- **Tradeoff**: Two patterns in the codebase. Servlet filters (`JwtAuthenticationFilter`) don't work in the gateway; a separate `JwtAuthGlobalFilter` mutates the reactive `ServerWebExchange`. Both use the same `JwtVerifier` from `common-security`, so the auth logic is shared even though the filter shape differs.

### No autoconfig in shared libraries — services explicitly `@Bean` what they use

- **Why**: Spring Boot's autoconfig is powerful but hides what's wired. For a learning project, every active bean should be visible in a service's own `@Configuration` class. Trade boilerplate for transparency.
- **Tradeoff**: A few extra `@Bean` lines per service. Worth it — newcomers can `grep` for a class name and see exactly where it's instantiated.

### Lombok 1.18.38 pinned + annotation processor path explicit

- **Why**: Spring Boot 3.3 manages Lombok to 1.18.32, which crashes on modern JDKs (`TypeTag :: UNKNOWN` error). `maven-compiler-plugin` 3.12+ also stopped auto-discovering processors from the regular classpath when `--release` is used. Both gotchas need explicit overrides in every service pom.
- **Tradeoff**: Six poms with the same Lombok override block. Documented in `feedback_lombok_maven_compiler.md` so the same trap doesn't catch the next service we add.

---

## 6. Local dev experience

### H2 in-memory for local, PostgreSQL in the Docker profile

- **Why**: `mvn spring-boot:run` on a service shouldn't require a Postgres container running. H2 in-memory with `ddl-auto: create-drop` boots in 2 seconds. The Docker profile swaps in PostgreSQL via env-var-driven config.
- **Tradeoff**: Local dev data doesn't survive a restart. Fine for the development loop; the Docker setup is the realistic one.

### Demo-friendly rate-limit values (1/sec, burst 3)

- **Why**: A sequential bash loop doesn't exceed the original 5-req/sec replenish rate, so the limiter never visibly fired. Lowered to 1/sec + 3 burst so reviewers see 429s appear during a basic test.
- **Tradeoff**: Production would tune up. Documented inline in `application.yml`.

### Eureka self-preservation OFF in local dev, ON in Docker

- **Why**: Self-preservation is a production safety net — it prevents mass eviction during network partitions. Locally, it makes stopped services linger in the dashboard, which is confusing. Off for dev clarity; default (on) in Docker.
- **Tradeoff**: A local Eureka instance won't behave exactly like production. Documented.

---

## 7. Things we explicitly did NOT do

| Not done | Why not | What production would need |
|---|---|---|
| Driver Service | Out of scope for the lab; hardcoded pool is fine for demos | Real platform: separate service with location, availability, history |
| Transactional outbox | Adds complexity (extra table + relay); current behavior is good enough for the lab's risk profile | Outbox table + a separate poller that ships rows to RabbitMQ |
| Saga orchestrator | Choreographed events are simpler and cover this use case | Saga state machine (Camunda, Temporal, etc.) for multi-step transactions |
| Distributed tracing (OpenTelemetry, Zipkin) | Correlation IDs in MDC are enough to grep logs locally | OTel instrumentation + a Jaeger / Tempo backend |
| Highly available gateway / Eureka | Single-replica is fine for one-laptop demo | N replicas behind a TCP load balancer, Eureka peers in a cluster |
| API versioning at the gateway | All endpoints are `/api/...` (effectively v1) | Route predicates on `/api/v1/**` vs `/api/v2/**` |
| Refresh tokens / token introspection | JWT TTL is 24h, manageable for the lab | OAuth2 refresh-token flow or remote introspection per-request |
| Database migrations (Flyway / Liquibase) | `hibernate.ddl-auto: update` works for the dev loop | Flyway + `ddl-auto: validate` to lock the schema |
| Centralized logging | `docker compose logs -f` is enough for the lab | ELK / Loki / Cloud-native log aggregation |
| Centralized config (Spring Cloud Config) | Static YAML + env vars cover everything we need | Config server with `@RefreshScope` for runtime tuning |
| Automated CI integration | `docker compose build` works on a developer laptop | GitHub Actions / GitLab CI building + pushing images, then deploying |

These aren't lazy omissions — each is a separate engineering problem that the lab doesn't ask for. Documented so reviewers and future-you know what's missing and why.
