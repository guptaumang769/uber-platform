-- Riders using the platform.
CREATE TABLE riders (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name  VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(32)
);

-- Ride requests. status starts REQUESTED; matching-service drives it forward.
CREATE TABLE ride_requests (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rider_id   BIGINT           NOT NULL REFERENCES riders (id),
    pickup_lat DOUBLE PRECISION NOT NULL,
    pickup_lng DOUBLE PRECISION NOT NULL,
    drop_lat   DOUBLE PRECISION NOT NULL,
    drop_lng   DOUBLE PRECISION NOT NULL,
    status     VARCHAR(32)      NOT NULL,
    created_at TIMESTAMPTZ      NOT NULL
);

CREATE INDEX ix_ride_requests_rider ON ride_requests (rider_id, created_at DESC);

-- Outbox: RideRequestedEvent is written here in the same tx as the ride request, then relayed
-- to Kafka by the OutboxPoller. Partial index keeps the "find unpublished" scan cheap.
CREATE TABLE outbox_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type   VARCHAR(255) NOT NULL,
    payload      TEXT         NOT NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at) WHERE published = FALSE;
