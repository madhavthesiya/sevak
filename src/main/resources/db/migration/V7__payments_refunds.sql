-- Task 2.6: several payment attempts per booking, at most one successful,
-- and refund state in one place only.

ALTER TABLE payments
    DROP CONSTRAINT payments_booking_id_key,
    ADD COLUMN created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN failure_reason TEXT,
    ALTER COLUMN paid_at TYPE TIMESTAMPTZ USING paid_at AT TIME ZONE 'Asia/Kolkata';

CREATE UNIQUE INDEX uq_one_successful_payment ON payments (booking_id) WHERE status = 'success';
CREATE INDEX idx_payments_booking ON payments (booking_id);

CREATE TABLE refunds (
    refund_id    INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id   INT           NOT NULL REFERENCES payments (payment_id),
    amount       NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    status       VARCHAR(20)   NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'processed', 'failed')),
    requested_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);

-- A payment can only be refunded once (a failed refund may be retried).
CREATE UNIQUE INDEX uq_one_active_refund ON refunds (payment_id) WHERE status <> 'failed';

INSERT INTO refunds (payment_id, amount, status, processed_at)
SELECT p.payment_id, p.amount,
       CASE WHEN c.refund_status = 'processed' THEN 'processed' ELSE 'pending' END,
       CASE WHEN c.refund_status = 'processed' THEN now() END
FROM payments p
LEFT JOIN cancellations c ON c.booking_id = p.booking_id
WHERE p.status = 'refunded';

ALTER TABLE cancellations
    ADD COLUMN cancelled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN cancelled_by INT REFERENCES users (user_id),
    DROP COLUMN refund_status;
