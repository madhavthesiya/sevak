-- ============================================================
--  SEVAK: triggers and stored procedures (repeatable)
--  Flyway re-runs this file whenever its contents change.
-- ============================================================

-- ── Keep updated_at current ─────────────────────────────────
CREATE OR REPLACE FUNCTION fn_touch_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_bookings_touch
BEFORE UPDATE ON bookings
FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();

CREATE OR REPLACE TRIGGER trg_provider_reviews_touch
BEFORE UPDATE ON provider_reviews
FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();

CREATE OR REPLACE TRIGGER trg_service_reviews_touch
BEFORE UPDATE ON service_reviews
FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();

-- ── Trigger functions and row-level security ────────────────
-- A trigger function normally runs as the user who fired it, so row-level security filters what it
-- sees. The ones that keep derived or audit data correct must see every row, so they are
-- SECURITY DEFINER: they run as their owner. Each sets a fixed search_path, so a caller can't put
-- look-alike tables in front of the real ones. Checks that only look at the caller's own booking stay
-- as the caller, and fail closed when they can't see it.

-- ── Keep a provider's cached rating and review count correct ─
-- A customer's review fires this, but the average covers every customer's reviews, and the
-- customer may not update service_providers at all. As the caller it would silently do nothing.
CREATE OR REPLACE FUNCTION fn_update_provider_avg_rating() RETURNS trigger
SECURITY DEFINER SET search_path = sevak, pg_temp AS $$
DECLARE
    v_provider_id INT;
BEGIN
    SELECT b.provider_id INTO v_provider_id
    FROM bookings b
    WHERE b.booking_id = CASE WHEN TG_OP = 'DELETE' THEN OLD.booking_id ELSE NEW.booking_id END;

    UPDATE service_providers sp
    SET avg_rating = stats.avg_rating, review_count = stats.review_count
    FROM (
        SELECT ROUND(AVG(pr.rating), 2) AS avg_rating, COUNT(*) AS review_count
        FROM provider_reviews pr
        JOIN bookings b ON b.booking_id = pr.booking_id
        WHERE b.provider_id = v_provider_id
    ) stats
    WHERE sp.provider_id = v_provider_id;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_after_review_change
AFTER INSERT OR UPDATE OR DELETE ON provider_reviews
FOR EACH ROW EXECUTE FUNCTION fn_update_provider_avg_rating();

-- ── Audit trail of every booking status, including the first ─
-- The application records who made the change with
-- SELECT set_config('sevak.user_id', ?, true) inside its transaction.
-- The API role has no INSERT on booking_status_log, so only this trigger can write it.
CREATE OR REPLACE FUNCTION fn_log_booking_status_change() RETURNS trigger
SECURITY DEFINER SET search_path = sevak, pg_temp AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO booking_status_log (booking_id, old_status, new_status, changed_by)
        VALUES (NEW.booking_id,
                CASE WHEN TG_OP = 'UPDATE' THEN OLD.status END,
                NEW.status,
                NULLIF(current_setting('sevak.user_id', true), '')::int);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_after_booking_status_update
AFTER INSERT OR UPDATE OF status ON bookings
FOR EACH ROW EXECUTE FUNCTION fn_log_booking_status_change();

-- ── Only allowed status changes, as listed in booking_status_transitions ─
CREATE OR REPLACE FUNCTION fn_check_status_transition() RETURNS trigger AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT EXISTS (
        SELECT 1 FROM booking_status_transitions t
        WHERE t.from_status = OLD.status AND t.to_status = NEW.status
    ) THEN
        RAISE EXCEPTION USING ERRCODE = 'SV409',
            MESSAGE = format('Illegal status change %s -> %s', OLD.status, NEW.status);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_booking_status_transition
BEFORE UPDATE OF status ON bookings
FOR EACH ROW EXECUTE FUNCTION fn_check_status_transition();

-- ── A booking has at least one item, and its subtotal equals them, checked at commit ─
-- Deferred because a booking and its items are inserted in separate statements.
-- SECURITY DEFINER because "booking not found" means "skip the check": as the caller, a booking it
-- couldn't see would pass unchecked.
CREATE OR REPLACE FUNCTION fn_check_booking_subtotal() RETURNS trigger
SECURITY DEFINER SET search_path = sevak, pg_temp AS $$
DECLARE
    v_booking_id INT;
    v_subtotal   NUMERIC(10,2);
    v_items      NUMERIC(12,2);
    v_count      INT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        v_booking_id := OLD.booking_id;
    ELSE
        v_booking_id := NEW.booking_id;
    END IF;

    SELECT subtotal INTO v_subtotal FROM bookings WHERE booking_id = v_booking_id;
    IF NOT FOUND THEN
        RETURN NULL; -- the booking itself is being deleted
    END IF;

    SELECT COALESCE(SUM(quantity * unit_price), 0), COUNT(*) INTO v_items, v_count
    FROM booking_items WHERE booking_id = v_booking_id;

    IF v_count = 0 THEN
        RAISE EXCEPTION USING ERRCODE = 'SV422',
            MESSAGE = format('Booking %s has no items', v_booking_id);
    END IF;

    IF v_items <> v_subtotal THEN
        RAISE EXCEPTION USING ERRCODE = 'SV422',
            MESSAGE = format('Booking %s subtotal %s does not match its items total %s',
                             v_booking_id, v_subtotal, v_items);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- CREATE OR REPLACE is not supported for constraint triggers.
DROP TRIGGER IF EXISTS trg_booking_subtotal ON bookings;
CREATE CONSTRAINT TRIGGER trg_booking_subtotal
AFTER INSERT OR UPDATE OF subtotal ON bookings
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION fn_check_booking_subtotal();

DROP TRIGGER IF EXISTS trg_booking_items_subtotal ON booking_items;
CREATE CONSTRAINT TRIGGER trg_booking_items_subtotal
AFTER INSERT OR UPDATE OR DELETE ON booking_items
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION fn_check_booking_subtotal();

-- ── Reviews only after the job is done ──────────────────────
CREATE OR REPLACE FUNCTION fn_check_review_allowed() RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM bookings WHERE booking_id = NEW.booking_id AND status = 'completed'
    ) THEN
        RAISE EXCEPTION USING ERRCODE = 'SV409',
            MESSAGE = format('Booking %s is not completed and cannot be reviewed', NEW.booking_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_provider_review_allowed
BEFORE INSERT ON provider_reviews
FOR EACH ROW EXECUTE FUNCTION fn_check_review_allowed();

CREATE OR REPLACE TRIGGER trg_service_review_allowed
BEFORE INSERT ON service_reviews
FOR EACH ROW EXECUTE FUNCTION fn_check_review_allowed();

-- ── A service review must be for a service in that booking ──
CREATE OR REPLACE FUNCTION fn_check_service_in_booking() RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM booking_items
        WHERE booking_id = NEW.booking_id AND service_id = NEW.service_id
    ) THEN
        RAISE EXCEPTION USING ERRCODE = 'SV422',
            MESSAGE = format('Service %s is not part of booking %s', NEW.service_id, NEW.booking_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_service_in_booking
BEFORE INSERT OR UPDATE ON service_reviews
FOR EACH ROW EXECUTE FUNCTION fn_check_service_in_booking();

-- ── A processed refund marks its payment as refunded ────────
-- The API role can't update payments, so a payment's status can only change this way.
CREATE OR REPLACE FUNCTION fn_mark_payment_refunded() RETURNS trigger
SECURITY DEFINER SET search_path = sevak, pg_temp AS $$
BEGIN
    IF NEW.status = 'processed' THEN
        UPDATE payments SET status = 'refunded' WHERE payment_id = NEW.payment_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_refund_processed
AFTER INSERT OR UPDATE OF status ON refunds
FOR EACH ROW EXECUTE FUNCTION fn_mark_payment_refunded();

-- ── Cancel a booking ────────────────────────────────────────
-- SELECT ... FOR UPDATE locks the row, so a concurrent confirm or cancel
-- waits here and then sees the new status.
-- Drop the old two-argument version; otherwise a two-argument CALL would be ambiguous.
DROP PROCEDURE IF EXISTS sp_cancel_booking(INT, TEXT);

CREATE OR REPLACE PROCEDURE sp_cancel_booking(
    p_booking_id   INT,
    p_reason       TEXT,
    p_cancelled_by INT DEFAULT NULL
)
LANGUAGE plpgsql AS $$
DECLARE
    v_status TEXT;
BEGIN
    SELECT status INTO v_status FROM bookings WHERE booking_id = p_booking_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'SV404',
            MESSAGE = format('Booking %s not found', p_booking_id);
    END IF;

    IF v_status NOT IN ('pending', 'confirmed') THEN
        RAISE EXCEPTION USING ERRCODE = 'SV409',
            MESSAGE = format('Booking %s is %s and cannot be cancelled', p_booking_id, v_status);
    END IF;

    UPDATE bookings SET status = 'cancelled' WHERE booking_id = p_booking_id;

    INSERT INTO cancellations (booking_id, reason, cancelled_by)
    VALUES (p_booking_id, p_reason, p_cancelled_by);

    -- The payment stays 'success' until the refund is actually processed.
    INSERT INTO refunds (payment_id, amount)
    SELECT payment_id, amount FROM payments
    WHERE booking_id = p_booking_id AND status = 'success';

    -- A cancelled booking gives its coupon use back.
    WITH released AS (
        DELETE FROM coupon_redemptions WHERE booking_id = p_booking_id RETURNING coupon_id
    )
    UPDATE coupons c SET used_count = c.used_count - 1
    FROM released r WHERE c.coupon_id = r.coupon_id;
END;
$$;

-- ── Confirm a booking ───────────────────────────────────────
CREATE OR REPLACE PROCEDURE sp_confirm_booking(p_booking_id INT)
LANGUAGE plpgsql AS $$
DECLARE
    v_status      TEXT;
    v_provider_id INT;
    v_start       TIMESTAMP; -- local (IST) wall-clock time
    v_end         TIMESTAMP;
    v_service_id  INT;
BEGIN
    SELECT status, provider_id,
           scheduled_start AT TIME ZONE 'Asia/Kolkata',
           scheduled_end   AT TIME ZONE 'Asia/Kolkata'
    INTO v_status, v_provider_id, v_start, v_end
    FROM bookings WHERE booking_id = p_booking_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'SV404',
            MESSAGE = format('Booking %s not found', p_booking_id);
    END IF;

    IF v_status <> 'pending' THEN
        RAISE EXCEPTION USING ERRCODE = 'SV409',
            MESSAGE = format('Booking %s is not pending. Current status: %s', p_booking_id, v_status);
    END IF;

    -- Every item must be a service this provider currently offers.
    SELECT bi.service_id INTO v_service_id
    FROM booking_items bi
    WHERE bi.booking_id = p_booking_id
      AND NOT EXISTS (
          SELECT 1 FROM provider_services ps
          WHERE ps.provider_id = v_provider_id
            AND ps.service_id = bi.service_id
            AND ps.is_active)
    ORDER BY bi.item_no
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'SV422',
            MESSAGE = format('Provider %s does not offer service ID %s.', v_provider_id, v_service_id);
    END IF;

    -- The whole job must fit inside one availability slot.
    IF NOT fn_provider_available(v_provider_id, v_start, v_end) THEN
        RAISE EXCEPTION USING ERRCODE = 'SV422',
            MESSAGE = format('Provider %s is not available on %s from %s to %s.', v_provider_id,
                             trim(to_char(v_start, 'Day')), to_char(v_start, 'HH24:MI'), to_char(v_end, 'HH24:MI'));
    END IF;

    UPDATE bookings SET status = 'confirmed' WHERE booking_id = p_booking_id;
END;
$$;

-- ── Does the whole job fit inside one of the provider's slots? ─
-- Takes local (IST) wall-clock times.
CREATE OR REPLACE FUNCTION fn_provider_available(p_provider_id INT, p_start TIMESTAMP, p_end TIMESTAMP)
RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
    SELECT p_start::date = p_end::date AND EXISTS (
        SELECT 1 FROM provider_availability pa
        WHERE pa.provider_id = p_provider_id
          AND pa.day_of_week = EXTRACT(ISODOW FROM p_start)
          AND pa.start_time <= p_start::time
          AND pa.end_time   >= p_end::time
    );
$$;

-- ── Release the slot of a request the provider never answered ─
-- SKIP LOCKED leaves alone any booking someone is working on right now.
-- SECURITY DEFINER: the scheduled job has no user, and must see every customer's pending requests.
-- It takes no arguments, so a caller can only run the expiry rule itself.
CREATE OR REPLACE FUNCTION fn_expire_pending_bookings() RETURNS INT
LANGUAGE plpgsql SECURITY DEFINER SET search_path = sevak, pg_temp AS $$
DECLARE
    v_booking_id INT;
    v_expired    INT := 0;
BEGIN
    FOR v_booking_id IN
        SELECT booking_id FROM bookings
        WHERE status = 'pending'
          AND (created_at < now() - interval '24 hours' OR scheduled_start <= now())
        ORDER BY booking_id
        FOR UPDATE SKIP LOCKED
    LOOP
        CALL sp_cancel_booking(v_booking_id, 'Expired: the provider did not respond in time');
        v_expired := v_expired + 1;
    END LOOP;
    RETURN v_expired;
END;
$$;

-- ── When a provider is busy, for the public free-slots screen ─
-- Row-level security hides other customers' bookings, but a free slot depends on all of them.
-- This returns only the time ranges of one provider's active bookings: no customer, price or address.
CREATE OR REPLACE FUNCTION fn_provider_busy_times(p_provider_id INT, p_from TIMESTAMPTZ, p_to TIMESTAMPTZ)
RETURNS TABLE (busy_start TIMESTAMPTZ, busy_end TIMESTAMPTZ)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = sevak, pg_temp AS $$
    SELECT scheduled_start, scheduled_end
    FROM bookings
    WHERE provider_id = p_provider_id
      AND status IN ('pending', 'confirmed', 'in_progress')
      AND scheduled_start < p_to
      AND scheduled_end > p_from;
$$;

-- ── Login ───────────────────────────────────────────────────
-- The API role can't read password hashes. Login, which runs before anyone is known,
-- gets the one hash for the email being checked, and nothing else.
CREATE OR REPLACE FUNCTION fn_login_credentials(p_email TEXT)
RETURNS TABLE (user_id INT, password_hash VARCHAR, role VARCHAR, status VARCHAR, profile_id INT)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = sevak, pg_temp AS $$
    SELECT u.user_id, u.password_hash, u.role, u.status,
           COALESCE(c.customer_id, sp.provider_id, a.admin_id)
    FROM users u
    LEFT JOIN customers c          ON c.user_id = u.user_id
    LEFT JOIN service_providers sp ON sp.user_id = u.user_id
    LEFT JOIN admins a             ON a.user_id = u.user_id
    WHERE LOWER(u.email) = LOWER(p_email);
$$;

-- ── Who may run what ────────────────────────────────────────
-- PostgreSQL lets everyone execute a new function. The SECURITY DEFINER ones run with the owner's
-- rights, so only the API role may call them, and the trigger-only ones can't be called at all.
REVOKE EXECUTE ON FUNCTION fn_update_provider_avg_rating(), fn_log_booking_status_change(),
                           fn_check_booking_subtotal(), fn_mark_payment_refunded()
    FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION fn_expire_pending_bookings(), fn_provider_busy_times(INT, TIMESTAMPTZ, TIMESTAMPTZ),
                           fn_login_credentials(TEXT)
    FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_expire_pending_bookings(), fn_provider_busy_times(INT, TIMESTAMPTZ, TIMESTAMPTZ),
                          fn_login_credentials(TEXT)
    TO sevak_app;
