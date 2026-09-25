-- Trips. ride_request_id is UNIQUE so a duplicate TripMatchedEvent (at-least-once Kafka delivery)
-- can't create two trips for the same ride request.
CREATE TABLE trips (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ride_request_id BIGINT         NOT NULL UNIQUE,
    rider_id        BIGINT         NOT NULL,
    driver_id       BIGINT         NOT NULL,
    status          VARCHAR(32)    NOT NULL,
    fare            NUMERIC(19, 2),
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL
);

-- Outbox: TripStatusEvent is written here in the same tx as the trip transition, then relayed to
-- Kafka by the OutboxPoller. The COMPLETED event is what drives the payment saga.
CREATE TABLE outbox_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type   VARCHAR(255) NOT NULL,
    payload      TEXT         NOT NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at) WHERE published = FALSE;
