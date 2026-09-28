# Uber Platform — Diagrams

Mermaid diagrams generated from the actual services (`matching-service`, `location-service`,
`trip-service`, `notification-service`) and the `KafkaTopics` / event records in `common`.
All render on GitHub.

- [1. High-Level Design (HLD)](#1-high-level-design-hld)
- [2. Ride request → match → live stream (sequence)](#2-ride-request--match--live-stream-sequence)
- [3. Trip state machine](#3-trip-state-machine)
- [4. Geospatial matching + assignment race](#4-geospatial-matching--assignment-race)

---

## 1. High-Level Design (HLD)

```mermaid
flowchart TB
    Browser["Rider / Driver UI<br/>(uber-ui · React + Leaflet)"] -->|"HTTPS · /api/v1/**"| GW["api-gateway<br/>Spring Cloud Gateway :8080"]

    subgraph Platform
      Eureka["discovery-server :8761"]
      Config["config-server :8888"]
    end
    GW -. "lb:// via Eureka" .-> Eureka

    GW --> Rider["rider-service :8081<br/>riders + rides"]
    GW --> Driver["driver-service :8082"]
    GW --> Location["location-service :8083"]
    GW --> Matching["matching-service :8084"]
    GW --> Pricing["pricing-service :8085"]
    GW --> Trip["trip-service :8086"]
    GW --> Payment["payment-service :8087"]
    GW --> Notif["notification-service :8088"]

    Rider -->|"RideRequested (outbox)"| K{{"Kafka"}}
    Driver -->|"DriverLocation"| K
    Matching -->|"TripMatched"| K
    Trip -->|"TripStatus (outbox)"| K
    Payment -->|"PaymentCompleted"| K

    K -->|"ride-requests"| Matching
    K -->|"driver-locations"| Location
    K -->|"driver-locations"| Notif
    K -->|"trip-events"| Trip
    K -->|"trip-events"| Driver
    K -->|"trip-events"| Payment
    K -->|"trip-events"| Notif

    Matching -->|"nearby? Feign + R4j"| Location
    Trip -->|"fare? Feign + R4j"| Pricing

    Location --> Redis[("Redis<br/>drivers:geo · surge · locks")]
    Matching --> Redis
    Pricing --> Redis
    Rider --> PGr[("riderdb")]
    Driver --> PGd[("driverdb")]
    Trip --> PGt[("tripdb")]
    Payment --> PGp[("paymentdb")]

    Notif ==>|"SSE: status + location<br/>(text/event-stream)"| Browser
```

The `common` module is a shared library (`ApiResponse`, enums, event records, `KafkaTopics`,
`GeoUtil`), not a running service — so it has no node above.

---

## 2. Ride request → match → live stream (sequence)

The end-to-end showcase: a rider taps *Request ride*, geospatial matching picks the nearest
free driver under a lock, a trip is minted, and the driver's position streams to the browser.

```mermaid
sequenceDiagram
    autonumber
    actor R as Rider (browser)
    participant GW as api-gateway
    participant RS as rider-service
    participant K as Kafka
    participant M as matching-service
    participant L as location-service
    participant T as trip-service
    participant N as notification-service

    R->>GW: POST /api/v1/rides {riderId, pickup, drop}
    GW->>RS: route → rider-service
    RS->>RS: persist RideRequest (REQUESTED) + outbox row
    RS-->>R: 201 RideResponse (status REQUESTED)
    RS->>K: RideRequested → topic ride-requests (outbox poller)

    K->>M: consume RideRequested
    M->>L: GET /locations/nearby?lat&lng&radiusKm (Feign + R4j)
    L->>L: Redis GEORADIUS on "drivers:geo" (nearest-first)
    L-->>M: [candidate drivers, nearest first]
    M->>M: SETNX "match:driver:{id}" (win the assignment lock)
    M->>K: TripMatched {rideRequestId, driverId, riderId} → topic trip-events

    K->>T: consume TripMatched
    T->>T: create Trip in MATCHED (fare via pricing-service)
    T->>K: TripStatus {tripId, MATCHED} → topic trip-events (outbox)

    Note over R,N: Browser opens EventSource → GET /api/v1/notifications/trips/{tripId}/stream
    K->>N: consume TripStatus + DriverLocation
    N-->>R: SSE "status" {tripId, status}
    N-->>R: SSE "location" {driverId, lat, lng, epoch}
    Note over N,R: One-way server push — status pill + moving car marker update live
```

---

## 3. Trip state machine

Legal transitions come straight from `trip-service`'s `TripStateMachine.ALLOWED`. The `Trip`
aggregate is created directly in `MATCHED`; `REQUESTED` is the `RideRequest` state before a
trip exists (shown as the entry).

```mermaid
stateDiagram-v2
    [*] --> REQUESTED : POST /rides (RideRequest)
    REQUESTED --> MATCHED : driver assigned (Trip created)
    MATCHED --> EN_ROUTE : POST /trips/{id}/enroute
    MATCHED --> CANCELLED : POST /trips/{id}/cancel
    EN_ROUTE --> ONGOING : POST /trips/{id}/start
    EN_ROUTE --> CANCELLED : POST /trips/{id}/cancel
    ONGOING --> COMPLETED : POST /trips/{id}/complete
    COMPLETED --> [*]
    CANCELLED --> [*]

    note right of ONGOING
        ONGOING cannot be cancelled.
        COMPLETE emits a TripStatus event
        that drives the payment capture.
    end note
```

`ONGOING` has no cancel edge; illegal transitions throw `IllegalStateException` → HTTP 409
`ILLEGAL_TRIP_TRANSITION`.

---

## 4. Geospatial matching + assignment race

Why the lock exists: two ride requests near the same driver would otherwise both pick it.

```mermaid
flowchart TD
    A["RideRequested consumed<br/>by matching-service"] --> B["GEORADIUS drivers:geo<br/>around pickup, nearest-first"]
    B --> C{"any candidates<br/>within radius?"}
    C -->|"no"| Z["no match — retry / expand radius"]
    C -->|"yes"| D["take nearest candidate"]
    D --> E{"SETNX match:driver:{id}<br/>lock won?"}
    E -->|"no — lost race"| F["skip to next-nearest driver"]
    F --> D
    E -->|"yes — 15s TTL"| G["assign driver"]
    G --> H["publish TripMatched → trip-events"]

    style G fill:#1f6f43,color:#fff
    style E fill:#8a6d00,color:#fff
```

The lock (`match:driver:{driverId}`, Redis `SETNX` with a 15s TTL) makes the assignment
atomic: the first request to win the key gets the driver; concurrent requests fall through to
the next-nearest candidate instead of double-booking. Driver positions only exist in Redis
`drivers:geo` after the driver sends location pings (`POST /api/v1/drivers/{id}/location`),
which is why the UI's *simulate movement* toggle is what makes a driver matchable.
