# Food Delivery Platform — Microservices

Migration of the Food Delivery Platform monolith (see [`monolith-reference/`](./monolith-reference/)) into four independently deployable Spring Boot microservices, with service discovery, an API gateway, and event-driven communication.

**Status:** Phase 1 of 11 — workspace scaffold complete.

---

## Workspace layout

```
food-delivery-platform-monolith/
├── services/                       # The four business microservices
│   ├── customer-service/           # :8081 → customer_db    (identity, profile, auth)
│   ├── restaurant-service/         # :8082 → restaurant_db  (restaurants + menu items)
│   ├── order-service/              # :8083 → order_db       (orders + order items)
│   └── delivery-service/           # :8084 → delivery_db    (delivery assignment + tracking)
│
├── infrastructure/                 # Cross-cutting platform services
│   ├── eureka-server/              # :8761 — Spring Cloud Netflix Eureka registry
│   └── api-gateway/                # :8080 — Spring Cloud Gateway (auth, routing, rate limit)
│
├── shared/                         # Small, focused libraries (NOT shared domain models)
│   ├── common-security/            # JWT verifier + X-User-* header constants
│   ├── common-events/              # RabbitMQ event POJOs (cross-service contract)
│   └── common-web/                 # ErrorResponse envelope + correlation-ID filter
│
├── docker/                         # Per-service Dockerfiles + docker-compose.yml
├── docs/                           # Architecture diagrams, API contracts
├── postman/                        # End-to-end Postman collection
├── scripts/                        # Build / dev helper scripts
│
├── monolith-reference/             # ORIGINAL monolith — read-only reference material
├── MIGRATION_LOG.md                # Per-phase decision log (the migration journey)
├── .gitignore
├── .editorconfig
└── README.md
```

---

## How this repo is structured

Each service is a **standalone Maven project** with its own `pom.xml` — there is **no parent POM**. This is deliberate: every service is fully self-contained and you can read it in isolation without chasing inheritance.

```bash
# Run one service locally
cd services/customer-service
./mvnw spring-boot:run

# Or with Docker (after Phase 10)
docker compose up
```

---

## Reading the migration

The migration is split into **11 phases**, each on its own git branch. To understand the *why* behind every decision, read [`MIGRATION_LOG.md`](./MIGRATION_LOG.md) top-to-bottom — every phase has an entry covering:

- What was done
- Why it was done that way
- Tradeoffs and known limitations
- What the next phase unblocks

| # | Phase | Branch |
|---|---|---|
| 1 | Workspace scaffold | `chore/multi-module-scaffold` |
| 2 | Shared libraries | `feat/common-libs-security-events-web` |
| 3 | Eureka server | `feat/eureka-server` |
| 4 | Customer Service | `feat/customer-service-extraction` |
| 5 | Restaurant Service | `feat/restaurant-service-extraction` |
| 6 | Order Service (Feign + Resilience4j) | `feat/order-service-extraction-with-feign` |
| 7 | Delivery Service | `feat/delivery-service-extraction` |
| 8 | RabbitMQ events + DLQ | `feat/rabbitmq-events-and-dlq` |
| 9 | API Gateway | `feat/api-gateway-with-jwt-and-ratelimit` |
| 10 | Dockerization + compose | `feat/docker-and-compose` |
| 11 | Postman + docs + migration log polish | `docs/postman-architecture-migration-log` |

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.x |
| Service discovery | Spring Cloud Netflix Eureka |
| API gateway | Spring Cloud Gateway |
| Inter-service sync | OpenFeign + Resilience4j |
| Inter-service async | RabbitMQ (topic exchange + dead-letter queue) |
| Database | PostgreSQL — **one container per service** |
| Auth | JWT (HS256), validated at the gateway, propagated as `X-User-*` headers |
| Containerization | Docker (multi-stage) + Docker Compose |

---

## Why these architectural choices?

Detailed reasoning lives in [`MIGRATION_LOG.md`](./MIGRATION_LOG.md). Short version:

- **Database per service** — every service owns its data; no joins or FKs cross service boundaries. Cross-domain links become ID references + REST (Feign) or events.
- **Snapshotting in the read model** — Order Service stores `customer_name`, `restaurant_name`, `menu_item_name`, `unit_price` at write time. Reads never fan out to other services; renaming a dish doesn't change historical orders.
- **JWT validated only at the gateway** — downstream services trust forwarded `X-User-Id` / `X-User-Role` headers and never validate tokens themselves. One place to rotate the secret, one place to fail.
- **Async for delivery, sync for menu validation** — placing an order *must* validate menu prices live (sync Feign). Assigning a driver does not need to happen on the request path (async RabbitMQ event).
- **No outbox table** — events publish via `@TransactionalEventListener(AFTER_COMMIT)`. Idempotent consumers + DLQ handle redelivery. A crash between commit and publish drops the event — acceptable tradeoff for this project; documented.

---

## Monolith reference

The original monolith is preserved verbatim under [`monolith-reference/`](./monolith-reference/) — it is **not** part of the build and is **not** deployed. It exists so you can:

- See where each piece of microservice code originated
- Diff old vs. new approaches when reviewing a phase
- Run it standalone (`cd monolith-reference && ./mvnw spring-boot:run`) to compare behavior end-to-end
