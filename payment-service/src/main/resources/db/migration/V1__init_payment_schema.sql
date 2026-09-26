-- Payments. trip_id is UNIQUE so a replayed trip-completed event maps back to the same payment
-- (charge exactly once) instead of inserting a duplicate.
CREATE TABLE payments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id     BIGINT         NOT NULL UNIQUE,
    amount      NUMERIC(19, 2) NOT NULL,
    captured_at TIMESTAMPTZ    NOT NULL
);
