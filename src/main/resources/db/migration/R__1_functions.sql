-- ============================================================
--  SEVAK: triggers and stored procedures (repeatable)
--  Flyway re-runs this file whenever its contents change.
-- ============================================================



-- ============================================================
--  TRIGGER 1: Auto-Update Provider avg_rating
-- ============================================================
-- PURPOSE: Every time a review is inserted, updated, or
--          deleted in provider_reviews, this trigger
--          automatically recalculates the provider's average
--          rating from ALL their reviews and updates the
--          avg_rating column in service_providers.
--
-- WHY THIS IS IMPORTANT:
--   Without this trigger, avg_rating would be stale
--   and require manual recalculation. This ensures
--   real-time data consistency at the database layer.
--
-- V2 CHANGE: Extended from INSERT-only to full lifecycle
--   (INSERT/UPDATE/DELETE) so edited or removed reviews
--   also trigger recalculation.
-- ============================================================

-- Step 1: Create the trigger function
CREATE OR REPLACE FUNCTION fn_update_provider_avg_rating()
RETURNS TRIGGER AS $$
DECLARE
    v_provider_id INT;
    v_new_avg     NUMERIC(3,2);
BEGIN
    -- Get provider_id from the affected booking.
    -- Use NEW for INSERT/UPDATE, OLD for DELETE (NEW is NULL on DELETE).
    IF TG_OP = 'DELETE' THEN
        SELECT b.provider_id INTO v_provider_id
        FROM bookings b
        WHERE b.booking_id = OLD.booking_id;
    ELSE
        SELECT b.provider_id INTO v_provider_id
        FROM bookings b
        WHERE b.booking_id = NEW.booking_id;
    END IF;

    -- Recalculate average rating from ALL remaining reviews
    -- Returns NULL if no reviews remain (correctly nullifies avg_rating)
    SELECT ROUND(AVG(pr.rating), 2) INTO v_new_avg
    FROM provider_reviews pr
    JOIN bookings b ON pr.booking_id = b.booking_id
    WHERE b.provider_id = v_provider_id;

    -- Update the provider's avg_rating
    UPDATE service_providers
    SET avg_rating = v_new_avg
    WHERE provider_id = v_provider_id;

    RAISE NOTICE 'Provider % avg_rating updated to % (trigger: %)',
        v_provider_id, v_new_avg, TG_OP;

    -- Must return OLD for DELETE, NEW for INSERT/UPDATE
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Step 2: Attach the trigger to the provider_reviews table
CREATE OR REPLACE TRIGGER trg_after_review_change
AFTER INSERT OR UPDATE OR DELETE ON provider_reviews
FOR EACH ROW
EXECUTE FUNCTION fn_update_provider_avg_rating();



-- ============================================================
--  TRIGGER 2: Auto-Log Booking Status Changes
-- ============================================================
-- PURPOSE: Every time a booking's status is updated,
--          this trigger automatically inserts a record
--          into the booking_status_log table.
--
-- WHY THIS IS IMPORTANT:
--   Maintains a complete audit trail of every status
--   transition without the application needing to
--   remember to log it manually.
-- ============================================================

CREATE OR REPLACE FUNCTION fn_log_booking_status_change()
RETURNS TRIGGER AS $$
BEGIN
    -- Only log if the status actually changed
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO booking_status_log (booking_id, log_time, status, remarks)
        VALUES (
            NEW.booking_id,
            NOW(),
            NEW.status,
            'Status changed from ' || OLD.status || ' to ' || NEW.status
        );

        RAISE NOTICE 'Booking % status logged: % -> %',
            NEW.booking_id, OLD.status, NEW.status;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_after_booking_status_update
AFTER UPDATE OF status ON bookings
FOR EACH ROW
EXECUTE FUNCTION fn_log_booking_status_change();


-- ============================================================
--  PROCEDURE 1: Process Booking Cancellation
-- ============================================================
-- PURPOSE: A single stored procedure that handles the
--          entire cancellation workflow:
--          1. Validates the booking exists and is cancellable
--          2. Updates the booking status to 'cancelled'
--          3. Inserts a row into the cancellations table
--          4. If a successful payment exists, marks it
--             as 'refunded' and sets refund_status
--          All wrapped in a TRANSACTION for ACID compliance.
--
-- USAGE:  CALL sp_cancel_booking(1, 'Customer changed plans');
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_cancel_booking(
    p_booking_id INT,
    p_reason     TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_current_status VARCHAR(30);
    v_payment_exists BOOLEAN;
BEGIN
    -- Step 1: Validate – Check if booking exists and get current status
    SELECT status INTO v_current_status
    FROM bookings
    WHERE booking_id = p_booking_id;

    IF v_current_status IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'SV404', MESSAGE = format('Booking %s not found', p_booking_id);
    END IF;

    -- Step 2: Validate – Cannot cancel already completed or cancelled bookings
    IF v_current_status IN ('completed', 'cancelled') THEN
        RAISE EXCEPTION USING ERRCODE = 'SV409', MESSAGE = format('Booking %s is %s and cannot be cancelled', p_booking_id, v_current_status);
    END IF;

    -- Step 3: Update booking status to 'cancelled'
    UPDATE bookings
    SET status = 'cancelled'
    WHERE booking_id = p_booking_id;
    -- (This fires trg_after_booking_status_update automatically!)

    -- Step 4: Check if a successful payment exists
    SELECT EXISTS (
        SELECT 1 FROM payments
        WHERE booking_id = p_booking_id AND status = 'success'
    ) INTO v_payment_exists;

    -- Step 5: Insert cancellation record with appropriate refund status
    IF v_payment_exists THEN
        -- Payment was made → mark for refund
        INSERT INTO cancellations (booking_id, reason, refund_status)
        VALUES (p_booking_id, p_reason, 'pending');

        -- Update the payment to refunded
        UPDATE payments
        SET status = 'refunded'
        WHERE booking_id = p_booking_id AND status = 'success';

        RAISE NOTICE 'Booking % cancelled. Payment refund initiated.', p_booking_id;
    ELSE
        -- No payment was made → no refund needed
        INSERT INTO cancellations (booking_id, reason, refund_status)
        VALUES (p_booking_id, p_reason, 'not_applicable');

        RAISE NOTICE 'Booking % cancelled. No payment to refund.', p_booking_id;
    END IF;
END;
$$;


-- ============================================================
--  PROCEDURE 2: Assign Provider to a Booking
-- ============================================================
-- PURPOSE: When a booking is in 'pending' status, this
--          procedure:
--          1. Validates the provider offers the requested service
--          2. Validates the provider is available on that day
--          3. Confirms the booking by changing status
--          Demonstrates complex validation logic with
--          multiple table lookups in a single procedure.
--
-- USAGE:  CALL sp_confirm_booking(6);
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_confirm_booking(
    p_booking_id INT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_current_status  VARCHAR(30);
    v_provider_id     INT;
    v_scheduled_day   VARCHAR(10);
    v_scheduled_time  TIME;
    v_is_available    BOOLEAN;
    v_offers_service  BOOLEAN;
    v_service_id      INT;
BEGIN
    -- Step 1: Get booking details
    SELECT b.status, b.provider_id, 
           TRIM(TO_CHAR(b.scheduled_date, 'Day')),
           b.scheduled_time
    INTO v_current_status, v_provider_id, v_scheduled_day, v_scheduled_time
    FROM bookings b
    WHERE b.booking_id = p_booking_id;

    IF v_current_status IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'SV404', MESSAGE = format('Booking %s not found', p_booking_id);
    END IF;

    IF v_current_status <> 'pending' THEN
        RAISE EXCEPTION USING ERRCODE = 'SV409', MESSAGE = format('Booking %s is not pending. Current status: %s', p_booking_id, v_current_status);
    END IF;

    -- Step 2: Get the first service in this booking
    SELECT bi.service_id INTO v_service_id
    FROM booking_items bi
    WHERE bi.booking_id = p_booking_id
    LIMIT 1;

    -- Step 3: Verify provider offers this service
    SELECT EXISTS (
        SELECT 1 FROM provider_services ps
        WHERE ps.provider_id = v_provider_id
          AND ps.service_id  = v_service_id
    ) INTO v_offers_service;

    IF NOT v_offers_service THEN
        RAISE EXCEPTION USING ERRCODE = 'SV422', MESSAGE = format('Provider %s does not offer service ID %s.', v_provider_id, v_service_id);
    END IF;

    -- Step 4: Verify provider is available on the scheduled day and time
    SELECT EXISTS (
        SELECT 1 FROM provider_availability pa
        WHERE pa.provider_id = v_provider_id
          AND pa.day_of_week = v_scheduled_day
          AND pa.start_time <= v_scheduled_time
          AND pa.end_time   >= v_scheduled_time
    ) INTO v_is_available;

    IF NOT v_is_available THEN
        RAISE EXCEPTION USING ERRCODE = 'SV422', MESSAGE = format('Provider %s is not available on %s at %s.', v_provider_id, v_scheduled_day, v_scheduled_time);
    END IF;

    -- Step 5: Confirm the booking
    UPDATE bookings
    SET status = 'confirmed'
    WHERE booking_id = p_booking_id;
    -- (This fires trg_after_booking_status_update automatically!)

    RAISE NOTICE 'Booking % confirmed. Provider % assigned for % at %.',
        p_booking_id, v_provider_id, v_scheduled_day, v_scheduled_time;
END;
$$;


