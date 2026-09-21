-- Drivers on the platform. status is one of OFFLINE / AVAILABLE / ON_TRIP.
-- current_lat/lng hold the last-known position (best-effort; Redis GEO in location-service is
-- the query index — this is just the durable last snapshot).
CREATE TABLE drivers (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(255)     NOT NULL,
    email       VARCHAR(255)     NOT NULL UNIQUE,
    vehicle     VARCHAR(255),
    status      VARCHAR(32)      NOT NULL,
    current_lat DOUBLE PRECISION,
    current_lng DOUBLE PRECISION
);
