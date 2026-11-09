-- Task 2.5: bookings get a real time range, money that can't drift,
-- a no-double-booking rule, status rules stored as data, and a safe audit log.

ALTER TABLE bookings
    ADD COLUMN scheduled_start TIMESTAMPTZ,
    ADD COLUMN scheduled_end   TIMESTAMPTZ,
    ADD COLUMN subtotal        NUMERIC(10,2),
    ADD COLUMN discount        NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at      TIMESTAMPTZ NOT NULL DEFAULT now();

-- Duration = sum of variant durations; items without a variant count as 60 minutes.
UPDATE bookings b
SET scheduled_start = (b.scheduled_date + b.scheduled_time) AT TIME ZONE 'Asia/Kolkata',
    scheduled_end   = (b.scheduled_date + b.scheduled_time) AT TIME ZONE 'Asia/Kolkata'
                      + interval '1 minute' * COALESCE((
                            SELECT SUM(COALESCE(sv.duration, 60) * bi.quantity)
                            FROM booking_items bi
                            LEFT JOIN service_variants sv ON sv.variant_id = bi.variant_id
                            WHERE bi.booking_id = b.booking_id), 60);

UPDATE bookings b
SET subtotal = GREATEST(b.total_amount, COALESCE((
        SELECT SUM(bi.quantity * bi.unit_price) FROM booking_items bi WHERE bi.booking_id = b.booking_id), 0));
UPDATE bookings SET discount = subtotal - total_amount;

ALTER TABLE bookings
    ALTER COLUMN scheduled_start SET NOT NULL,
    ALTER COLUMN scheduled_end   SET NOT NULL,
    ALTER COLUMN subtotal        SET NOT NULL,
    DROP COLUMN scheduled_date,
    DROP COLUMN scheduled_time,
    ADD CONSTRAINT chk_booking_time     CHECK (scheduled_end > scheduled_start),
    ADD CONSTRAINT chk_booking_discount CHECK (discount >= 0),
    ADD CONSTRAINT chk_booking_total    CHECK (total_amount = subtotal - discount);

-- No double booking. A pending booking holds its slot, so two customers can't both
-- request the same time while the provider is deciding. Trade-off: an ignored request
-- blocks the slot until it is cancelled.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings ADD CONSTRAINT ex_provider_no_double_booking
    EXCLUDE USING gist (
        provider_id WITH =,
        tstzrange(scheduled_start, scheduled_end, '[)') WITH &&
    ) WHERE (status IN ('pending', 'confirmed', 'in_progress'));

-- Allowed status changes, as data rather than code (enforced by a trigger in R__1).
CREATE TABLE booking_status_transitions (
    from_status VARCHAR(30) NOT NULL,
    to_status   VARCHAR(30) NOT NULL,
    PRIMARY KEY (from_status, to_status)
);

INSERT INTO booking_status_transitions (from_status, to_status) VALUES
    ('pending',     'confirmed'),
    ('pending',     'cancelled'),
    ('confirmed',   'in_progress'),
    ('confirmed',   'cancelled'),
    ('in_progress', 'completed');

-- The old key (booking_id, log_time) with NOW() broke if one transaction changed
-- a status twice, because NOW() is the transaction's start time.
CREATE TABLE booking_status_log_v2 (
    log_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id INT         NOT NULL REFERENCES bookings (booking_id) ON DELETE CASCADE,
    old_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_by INT         REFERENCES users (user_id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    remarks    TEXT
);

INSERT INTO booking_status_log_v2 (booking_id, old_status, new_status, changed_at, remarks)
SELECT booking_id,
       LAG(status) OVER (PARTITION BY booking_id ORDER BY log_time),
       status,
       log_time AT TIME ZONE 'Asia/Kolkata',
       remarks
FROM booking_status_log
ORDER BY booking_id, log_time;

DROP TABLE booking_status_log;
ALTER TABLE booking_status_log_v2 RENAME TO booking_status_log;
CREATE INDEX idx_status_log_booking ON booking_status_log (booking_id, changed_at);
