# Uber Platform — Ride-Hailing Backend

An Uber-style ride-hailing backend built as **Spring Cloud microservices**, whose headline is
**real-time geospatial matching** — the nearest free driver is found with a Redis geo query,
claimed under a distributed lock to win the assignment race, and then streamed live to the
rider's map over Server-Sent Events. Surge pricing rides on top.

Java 21 · Spring Boot 3.3.5 · Spring Cloud 2023.0.3 · PostgreSQL · Redis · Kafka.

📐 Diagrams (HLD, ride/match sequence, trip state machine, matching race): **[DIAGRAMS.md](DIAGRAMS.md)**
🗺️ Front end (React + Leaflet live map): **[../uber-ui](../uber-ui)**

---

## Services

| Service | Port | Store | Responsibility |
|---|---|---|---|
| api-gateway | 8080 | — | routing (`lb://`), CORS, edge circuit breakers + fallbacks |
| config-server | 8888 | — | centralized configuration |
| discovery-server | 8761 | — | Eureka service registry |
| rider-service | 8081 | PostgreSQL | riders **and** rides; publishes `RideRequested` (Outbox) |
| driver-service | 8082 | PostgreSQL | drivers, online/offline, location pings → `DriverLocation` |
| location-service | 8083 | **Redis (GEO)** | `GEOADD`/`GEORADIUS` on `drivers:geo`; nearby-driver queries |
| matching-service | 8084 | **Redis (locks)** | nearest-driver match + `SETNX` assignment lock (the showcase) |
| pricing-service | 8085 | **Redis (counters)** | fare estimate + **surge** from demand/supply per geo-cell |
| trip-service | 8086 | PostgreSQL | trip lifecycle **state machine**; `TripStatus` events (Outbox) |
| payment-service | 8087 | PostgreSQL | captures fare on trip `COMPLETED` (Kafka-driven) |
| notification-service | 8088 | — (in-memory) | **SSE** stream of trip status + driver location to the browser |
| common | — | — | shared library: `ApiResponse<T>`, enums, event records, `KafkaTopics`, `GeoUtil` |

That's **11 bootable services + 1 shared library (`common`)** = 12 Maven modules.
**Postgres** holds the durable aggregates (riders, drivers, trips, payments); **Redis** is the
low-latency plane (driver geo index, surge counters, assignment locks); **Kafka** is the async
event bus that decouples request → match → trip → notification → payment.

---

## Signature features

### 1. Geospatial matching + the assignment race — the headline

When a rider requests a ride, `rider-service` persists a `RideRequest` and publishes
`RideRequested` (via the transactional **Outbox**, so there's no dual-write). `matching-service`
consumes it and:

1. asks `location-service` for drivers near the pickup — a Redis **`GEORADIUS`** over the
   `drivers:geo` set, returned **nearest-first** (the Feign call is Resilience4j-guarded, empty
   fallback on failure);
2. walks the candidates and tries to claim one with a **`SETNX` lock** on
   `match:driver:{driverId}` (15s TTL). The first request to win the key gets that driver;
   concurrent requests for the same driver **lose the race and fall through to the next-nearest**
   — no double-booking;
3. publishes `TripMatched`, which `trip-service` turns into a `Trip` (in `MATCHED`) and
   `driver-service` uses to flip the driver to `ON_TRIP`.

**Why a lock?** Matching is concurrent and event-driven. Without an atomic claim, two nearby
requests would both select the same closest driver. `SETNX` makes "pick this driver" a
compare-and-set; the TTL guarantees the lock releases even if a matcher crashes mid-assignment.

**Why Redis geo?** Driver positions change every couple of seconds and "who's within 5 km of
this point, sorted by distance" is a native Redis operation (`GEOADD` on write, `GEORADIUS` on
read) — far cheaper than repeatedly scanning a SQL table with Haversine math.

### 2. Real-time driver tracking over SSE

The rider's map shows the car **moving in real time**. `driver-service` publishes each GPS ping
to the `driver-locations` topic; `notification-service` consumes trip-status and location events
and pushes them to the browser as **Server-Sent Events** on
`GET /api/v1/notifications/trips/{tripId}/stream`. It emits exactly two named events:

- **`status`** → `{ tripId, status }` — the trip lifecycle transitions;
- **`location`** → `{ driverId, lat, lng, epoch }` — driver GPS pings.

**Why SSE, not WebSocket or polling?** This channel is **one-way, server → browser**. SSE is a
plain HTTP/1.1 text stream (`text/event-stream`) — no upgrade handshake, no sub-protocol, and
the browser's `EventSource` auto-reconnects. A WebSocket's bidirectional channel would be
over-engineered (the rider never pushes back here); polling would add latency and load for data
the server can just push the instant it changes.

### 3. Surge pricing

`pricing-service` computes `fare = (baseFare + distanceKm × perKmRate) × surge`. The surge
multiplier is `1 + max(0, demand − supply) / max(1, supply)`, clamped to `[1, 3]`. Demand and
supply are tracked as **Redis counters per geohash cell** (`surge:demand:{cell}` /
`surge:supply:{cell}`) over a sliding 5-minute window — so repeated ride requests in a hot area
push the multiplier up, and it decays as the window expires.

**Why per-cell counters in Redis?** Surge is inherently local and time-windowed; `INCR` with a
TTL gives an O(1), self-expiring approximation without a heavyweight stream-processing job.

**Resilience throughout:** cross-service calls (matching → location, trip → pricing) are
OpenFeign clients guarded by **Resilience4j** (circuit breaker + fallback); the gateway wraps
pricing/trip routes with edge circuit breakers and 503 fallbacks. Event publishing uses the
**Outbox pattern** (rider-service, trip-service) for guaranteed delivery.

---

## Run it

**Prerequisites:** JDK 21 (Temurin or Corretto — needed only if you build/test locally) ·
Maven · Docker Desktop.

⚠️ **Heavy stack** — Postgres + Redis + Kafka + 8 service JVMs (plus config/discovery/gateway)
want **~6–8 GB RAM**. Give Docker Desktop enough memory first. To run lighter, bring up the
infra plus just `rider, driver, location, matching, pricing, trip, notification` for the core
ride flow.

```bash
git clone https://github.com/guptaumang769/uber-platform.git
cd uber-platform

docker compose up -d --build
# Eureka:  http://localhost:8761   (wait until services show as registered)
# Gateway: http://localhost:8080

# Once services are registered, verify the gateway:
curl localhost:8080/actuator/health   # → {"status":"UP"}
```

Then start the UI (live Leaflet map):

```bash
cd ../uber-ui
npm install
npm run dev          # http://localhost:5176  (proxies /api → :8080)
```

### Example ride flow (through the gateway)

Seeded: riders `1` (Asha) & `2` (Vikram); drivers `1`–`5` near Bangalore (12.97, 77.59).

```bash
# 1. (optional) register a rider — or use seeded id 1
curl -XPOST localhost:8080/api/v1/riders -H 'Content-Type: application/json' \
  -d '{"name":"Asha","email":"asha@example.com","phone":"+919000000001"}'

# 2. bring a driver online and start pinging location (this is what makes them matchable —
#    driver positions only enter Redis via these pings)
curl -XPOST localhost:8080/api/v1/drivers/1/online
curl -XPOST localhost:8080/api/v1/drivers/1/location -H 'Content-Type: application/json' \
  -d '{"lat":12.9716,"lng":77.5946}'

# 3. estimate the fare (fare + surgeMultiplier + distanceKm)
curl "localhost:8080/api/v1/pricing/estimate?pickupLat=12.9716&pickupLng=77.5946&dropLat=12.978&dropLng=77.605"

# 4. request the ride → returns a RideRequest (status REQUESTED); matching runs async
curl -XPOST localhost:8080/api/v1/rides -H 'Content-Type: application/json' \
  -d '{"riderId":1,"pickupLat":12.9716,"pickupLng":77.5946,"dropLat":12.978,"dropLng":77.605}'

# 5. watch the trip; then drive it through its lifecycle
curl localhost:8080/api/v1/trips/1
curl -XPOST localhost:8080/api/v1/trips/1/enroute
curl -XPOST localhost:8080/api/v1/trips/1/start
curl -XPOST localhost:8080/api/v1/trips/1/complete   # emits TripStatus → payment capture

# 6. watch it all live (trip status + moving driver marker):
#    open the uber-ui, or tail the SSE stream directly:
curl -N localhost:8080/api/v1/notifications/trips/1/stream
```

---

## Tests

```bash
mvn install -DskipTests     # build reactor (parent + common) first
mvn test                    # Mockito unit tests per service — 28 tests pass
```

Covered: rider register + ride request/outbox, driver online/offline/location publish, the
**matching decision + assignment lock (win vs. lose the race)**, location geo add/nearby, surge
math (supply ≥ demand → 1.0, demand spike → higher), the **trip state-machine transitions**
(legal vs. `IllegalStateException`), and notification event fan-out.

> **JDK note:** build and test on **JDK 21** (e.g. Temurin/Corretto 21). On a very recent
> default JDK (25 is common now) Mockito's inline mock maker fails to instrument classes
> (a ByteBuddy/JDK-internals issue, not a code bug), and you'll see `Could not initialize
> ... mockito`. Point `JAVA_HOME` at a JDK 21 and the **28 tests pass**:
>
> ```bash
> JAVA_HOME=/path/to/jdk-21 mvn test
> ```

---

## Are microservices justified here?

Yes — the domains genuinely differ in data model, scaling profile, and failure mode. Driver
locations are a high-write Redis geo index; trips are a durable Postgres state machine; matching
is CPU-light but latency-sensitive and concurrency-critical; notifications hold thousands of
long-lived SSE connections. Splitting them lets each scale independently (you shard location and
matching hard on a busy Friday night without touching payments) and fail independently (a
pricing outage degrades to a circuit-breaker fallback instead of blocking rides). Kafka between
them keeps the request path fast and the pipeline resilient to any one consumer being slow.

See **[DIAGRAMS.md](DIAGRAMS.md)** for the HLD, the ride/match sequence, the trip state machine,
and the matching-race flow.
