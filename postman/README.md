# Postman — end-to-end test collection

One JSON file. ~29 requests across 8 folders. Runs against the API Gateway on `:8080` and exercises the full microservice flow including event-driven status updates and the rate limiter.

## What's in the collection

| Folder | What it tests |
|---|---|
| **00 — Health checks** | Gateway is up, all routes loaded, circuit breakers reachable |
| **01 — Auth** | Register flow — captures `{{token}}` + `{{customerId}}` |
| **02 — Customer** | `/api/customers/me` read + update |
| **03 — Restaurant** | Create restaurant (triggers Feign call to promote owner role), re-login as RESTAURANT_OWNER, add menu item, public search, public menu read |
| **04 — Order** | Place order (Feign to Customer + Restaurant, snapshot fields), read it, list history |
| **05 — Delivery** | Auto-created delivery (via `OrderPlacedEvent`), status updates round-trip back to the order (via `DeliveryStatusUpdatedEvent`) |
| **06 — Negative paths** | 401 on missing/invalid token, 404 on `/api/internal/**`, 400 on validation failure |
| **07 — Rate limit demo** | 5 rapid POSTs to `/api/orders` — expect at least one 429 |

Variables chain through: `{{token}}` → `{{restaurantId}}` → `{{menuItemId}}` → `{{orderId}}` → `{{deliveryId}}`. Nothing to copy-paste manually.

## Prerequisites

The full stack must be running:

```bash
cp .env.example .env
docker compose up -d --build
```

Wait until `docker compose ps` shows everything `healthy` (about 2 minutes on a cold start).

## Run in Postman

1. **Import** the collection
   `File → Import → drag postman/food-delivery.postman_collection.json`

2. (Optional) **Override `baseUrl`** if your gateway isn't on `localhost:8080`
   Click the collection name → Variables tab → edit `baseUrl`'s Current Value

3. **Run the collection**
   Right-click the collection → "Run collection" → click the big "Run Food Delivery Platform — E2E" button.
   - **Set Delay: 2000 ms** before clicking Run. This handles the RabbitMQ event round-trip between order placement and delivery creation.
   - Folder 07 (rate limit) needs the delay back to 0 to actually trip the limiter — run it separately with no delay.

Expected outcome: all tests in folders 00–05 pass green. Folder 06 deliberately tests failure paths (which "pass" when they fail correctly). Folder 07 shows a mix of 201 and 429.

## Run via Newman (CLI / CI)

```bash
# Install once
npm install -g newman

# Run the full collection with delay for event propagation
newman run postman/food-delivery.postman_collection.json --delay-request 2000

# Run only one folder
newman run postman/food-delivery.postman_collection.json --folder "04 — Order"

# Export an HTML report (handy for the lab deliverable)
npm install -g newman-reporter-htmlextra
newman run postman/food-delivery.postman_collection.json \
  --delay-request 2000 \
  -r cli,htmlextra \
  --reporter-htmlextra-export newman-report.html
```

CI tip: a green Newman run is a meaningful integration test of the whole platform. Wire it into `.github/workflows/` after `docker compose up`.

## Timing — the only thing you have to know

The event flow is asynchronous:

```
POST /api/orders → 201 (order saved)
  ↓ OrderPlacedEvent → RabbitMQ → delivery-service (~100–500 ms)
GET /api/deliveries/order/{orderId} → 200 if event already consumed, 404 if not
```

Without the **2000 ms delay**, the "Get delivery by order" test in folder 05 can race the event consumer and fail with a 404. Two mitigations are built into the collection:

- The first delivery lookup test (`Get delivery by order`) prints a note in its test script explaining the 404 case.
- Subsequent status-change assertions accept either the old or the new status (`PLACED or CONFIRMED`, `CONFIRMED or OUT_FOR_DELIVERY`, etc.) so a fast run still passes — just with less precision.

For a clean run with strict status assertions, set the 2000 ms delay.

## Re-running the collection

The Register request uses a unique timestamped username per run (`john_<epoch_ms>`), so you can run the collection multiple times without "username already taken" failures. State that accumulates in the databases (restaurants, orders, deliveries) is owned by that run's user — earlier runs' data stays isolated.

To wipe everything between runs (clean state):

```bash
docker compose down -v && docker compose up -d --build
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `Get delivery by order` returns 404 | Event hasn't propagated yet | Set Runner Delay to 2000 ms, or re-run that request alone after a few seconds |
| `Place order` returns 503 | Customer Service or Restaurant Service is down or circuit-broken | `docker compose ps` to see which is unhealthy; logs to see why |
| All requests in 07 succeed (no 429s) | Runner has a non-zero Delay so requests don't burst | Set Delay to 0 ms before running just the rate-limit folder |
| `Add menu item` returns 403 | Re-login step didn't run (you used the CUSTOMER-role token) | Run the "Re-login (role is now RESTAURANT_OWNER)" request before adding menu items |
| `400` on register with no fields | Expected — that's what the negative-path test asserts | Not a bug |
