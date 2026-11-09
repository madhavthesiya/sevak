-- ============================================================
--  SEVAK – Data Integrity Checks
--  Each query must return 0 rows.
-- ============================================================
SET search_path = sevak;

-- 1. Booking total = items subtotal - coupon discount
WITH calc AS (
    SELECT b.booking_id, b.total_amount,
           SUM(bi.quantity * bi.unit_price) AS subtotal,
           MAX(c.discount_type)             AS discount_type,
           MAX(c.discount_value)            AS discount_value
    FROM bookings b
    JOIN booking_items bi ON bi.booking_id = b.booking_id
    LEFT JOIN coupons c   ON c.coupon_id  = b.coupon_id
    GROUP BY b.booking_id, b.total_amount
), expected AS (
    SELECT *, subtotal - CASE discount_type
                            WHEN 'percentage' THEN ROUND(subtotal * discount_value / 100, 2)
                            WHEN 'flat'       THEN discount_value
                            ELSE 0
                         END AS expected_total
    FROM calc
)
SELECT * FROM expected WHERE total_amount <> expected_total;

-- 2. Provider works in the same city as the job
SELECT b.booking_id, sp.city_id AS provider_city, a.city_id AS job_city
FROM bookings b
JOIN service_providers sp ON sp.provider_id = b.provider_id
JOIN locations l          ON l.location_id  = b.location_id
JOIN areas a              ON a.area_id      = l.area_id
WHERE sp.city_id <> a.city_id;

-- 3. Stored avg_rating matches the real reviews
SELECT sp.provider_id, sp.avg_rating, ROUND(AVG(pr.rating), 2) AS actual
FROM service_providers sp
LEFT JOIN bookings b          ON b.provider_id = sp.provider_id
LEFT JOIN provider_reviews pr ON pr.booking_id = b.booking_id
GROUP BY sp.provider_id, sp.avg_rating
HAVING sp.avg_rating IS DISTINCT FROM ROUND(AVG(pr.rating), 2);

-- 4. The newest status-log row matches the booking's current status
SELECT b.booking_id, b.status, l.new_status
FROM bookings b
LEFT JOIN LATERAL (
    SELECT new_status FROM booking_status_log
    WHERE booking_id = b.booking_id
    ORDER BY log_id DESC LIMIT 1
) l ON TRUE
WHERE l.new_status IS DISTINCT FROM b.status;
