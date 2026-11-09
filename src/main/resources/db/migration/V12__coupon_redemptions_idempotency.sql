-- Phase 4: one coupon use per customer, and safe retries when creating a booking.

CREATE TABLE coupon_redemptions (
    coupon_id   INT         NOT NULL REFERENCES coupons (coupon_id) ON DELETE CASCADE,
    customer_id INT         NOT NULL REFERENCES customers (customer_id) ON DELETE CASCADE,
    booking_id  INT         NOT NULL UNIQUE REFERENCES bookings (booking_id) ON DELETE CASCADE,
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (coupon_id, customer_id)
);

INSERT INTO coupon_redemptions (coupon_id, customer_id, booking_id)
SELECT DISTINCT ON (coupon_id, customer_id) coupon_id, customer_id, booking_id
FROM bookings
WHERE coupon_id IS NOT NULL AND status <> 'cancelled'
ORDER BY coupon_id, customer_id, booking_id;

-- A client that retries POST /bookings with the same key gets the same booking back.
CREATE TABLE idempotency_keys (
    user_id         INT          NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    booking_id      INT          REFERENCES bookings (booking_id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, idempotency_key)
);
