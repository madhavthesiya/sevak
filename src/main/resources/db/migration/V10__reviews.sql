-- Task 2.9: review timestamps, and a stored review count next to the cached average.
-- The rules (only after completion, service must be in the booking) are triggers in R__1.

ALTER TABLE provider_reviews
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE service_reviews
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE service_providers ADD COLUMN review_count INT NOT NULL DEFAULT 0 CHECK (review_count >= 0);

UPDATE service_providers sp
SET review_count = (
    SELECT COUNT(*) FROM provider_reviews pr
    JOIN bookings b ON b.booking_id = pr.booking_id
    WHERE b.provider_id = sp.provider_id);
