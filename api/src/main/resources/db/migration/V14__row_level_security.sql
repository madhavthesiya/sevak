-- ============================================================
--  Phase 8: row-level security
--
--  The API connects as sevak_app. It doesn't own any table, isn't a superuser and can't bypass
--  row-level security, so the policies below decide which rows each request can see or change,
--  even if a Java ownership check is missing. Flyway and the background jobs connect as the owner.
--
--  Each transaction tells the database who is calling with
--      SELECT set_config('sevak.user_id', <user id>, true)
--  and the database works out for itself whether that user is a customer, a provider or an admin.
--  With no user set, private rows are invisible: a forgotten setting hides data, it can't leak it.
-- ============================================================

-- ── The role the API connects as ───────────────────────────
-- Roles belong to the whole server, not one database, so create it only once.
-- The db/callback/afterMigrate.sql script lets it log in with the configured password.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'sevak_app') THEN
        CREATE ROLE sevak_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
END
$$;

-- ── Who is calling ──────────────────────────────────────────
-- Policies call these inside (SELECT ...), so each runs once per query, not once per row.
-- PARALLEL SAFE: PostgreSQL treats a new function as parallel-unsafe, and one unsafe function in a policy
-- stops every query on that table from using parallel workers. These only read a table and a setting,
-- and parallel workers get the setting from the leader.
CREATE FUNCTION app_user_id() RETURNS INT
LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT NULLIF(current_setting('sevak.user_id', true), '')::int
$$;

CREATE FUNCTION app_customer_id() RETURNS INT
LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT customer_id FROM sevak.customers WHERE user_id = sevak.app_user_id()
$$;

CREATE FUNCTION app_provider_id() RETURNS INT
LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT provider_id FROM sevak.service_providers WHERE user_id = sevak.app_user_id()
$$;

CREATE FUNCTION app_is_admin() RETURNS BOOLEAN
LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT EXISTS (SELECT 1 FROM sevak.admins WHERE user_id = sevak.app_user_id())
$$;

-- ── What the API may do at all (table privileges) ──────────
-- No TRUNCATE, no DDL, and the catalog is read-only. Views and functions are granted in R__1 and R__2,
-- which recreate them.
GRANT USAGE ON SCHEMA sevak TO sevak_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA sevak TO sevak_app;

GRANT SELECT ON cities, areas, categories, services, service_variants, booking_status_transitions,
                provider_service_areas, admins, admin_permissions
    TO sevak_app;

-- Every column but the password hash. Login reads one hash through fn_login_credentials (R__1).
GRANT SELECT (user_id, email, role, status, created_at, full_name, phone) ON users TO sevak_app;
GRANT INSERT ON users TO sevak_app;

GRANT SELECT, INSERT ON customers TO sevak_app;
GRANT SELECT, INSERT ON locations TO sevak_app;
GRANT UPDATE (latitude) ON locations TO sevak_app; -- only what the address upsert's DO UPDATE touches; a shared
                                                  -- map point's street can't be changed by one customer
GRANT SELECT ON coupons TO sevak_app;
GRANT UPDATE (used_count) ON coupons TO sevak_app;

GRANT SELECT, INSERT ON bookings TO sevak_app;
GRANT UPDATE (status) ON bookings TO sevak_app; -- the only column the app changes; SELECT FOR UPDATE needs it too
GRANT SELECT, INSERT ON booking_items, payments, cancellations, provider_reviews, service_reviews TO sevak_app;
GRANT SELECT ON booking_status_log TO sevak_app; -- written only by its trigger, so it can't be forged
GRANT SELECT, INSERT, UPDATE ON customer_addresses, idempotency_keys, provider_services TO sevak_app;
-- Policies check whose row it is; column grants decide which values a caller may choose. The other
-- columns take their defaults, which are the safe values: a refund starts pending, a complaint open.
GRANT SELECT, UPDATE ON refunds, complaints TO sevak_app;
GRANT INSERT (payment_id, amount) ON refunds TO sevak_app;
GRANT INSERT (booking_id, subject, description, raised_by) ON complaints TO sevak_app;
-- No INSERT: the app never uploads documents, so the role can't create one already marked verified.
GRANT SELECT, UPDATE ON provider_documents TO sevak_app;
GRANT SELECT, INSERT, DELETE ON coupon_redemptions, provider_availability TO sevak_app;
GRANT SELECT ON service_providers TO sevak_app;
-- Registration only: a new provider starts unverified, with no rating or reviews.
GRANT INSERT (user_id, city_id, bio, experience_years) ON service_providers TO sevak_app;
GRANT UPDATE (verification_status) ON service_providers TO sevak_app;

-- ── Row-level security ──────────────────────────────────────
-- FORCE applies the policies to the table owner too. Superusers and roles with BYPASSRLS still skip
-- them, which is what Flyway, the jobs and the SECURITY DEFINER functions rely on.
ALTER TABLE bookings            ENABLE ROW LEVEL SECURITY;
ALTER TABLE booking_items       ENABLE ROW LEVEL SECURITY;
ALTER TABLE booking_status_log  ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments            ENABLE ROW LEVEL SECURITY;
ALTER TABLE refunds             ENABLE ROW LEVEL SECURITY;
ALTER TABLE cancellations       ENABLE ROW LEVEL SECURITY;
ALTER TABLE coupon_redemptions  ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys    ENABLE ROW LEVEL SECURITY;
ALTER TABLE complaints          ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_addresses  ENABLE ROW LEVEL SECURITY;
ALTER TABLE provider_documents  ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_providers   ENABLE ROW LEVEL SECURITY;
ALTER TABLE provider_services   ENABLE ROW LEVEL SECURITY;
ALTER TABLE provider_availability ENABLE ROW LEVEL SECURITY;
ALTER TABLE provider_reviews    ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_reviews     ENABLE ROW LEVEL SECURITY;

ALTER TABLE bookings            FORCE ROW LEVEL SECURITY;
ALTER TABLE booking_items       FORCE ROW LEVEL SECURITY;
ALTER TABLE booking_status_log  FORCE ROW LEVEL SECURITY;
ALTER TABLE payments            FORCE ROW LEVEL SECURITY;
ALTER TABLE refunds             FORCE ROW LEVEL SECURITY;
ALTER TABLE cancellations       FORCE ROW LEVEL SECURITY;
ALTER TABLE coupon_redemptions  FORCE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys    FORCE ROW LEVEL SECURITY;
ALTER TABLE complaints          FORCE ROW LEVEL SECURITY;
ALTER TABLE customer_addresses  FORCE ROW LEVEL SECURITY;
ALTER TABLE provider_documents  FORCE ROW LEVEL SECURITY;
ALTER TABLE service_providers   FORCE ROW LEVEL SECURITY;
ALTER TABLE provider_services   FORCE ROW LEVEL SECURITY;
ALTER TABLE provider_availability FORCE ROW LEVEL SECURITY;
ALTER TABLE provider_reviews    FORCE ROW LEVEL SECURITY;
ALTER TABLE service_reviews     FORCE ROW LEVEL SECURITY;

-- ── Bookings: the customer, the provider and admins ────────
CREATE POLICY bookings_read ON bookings FOR SELECT
    USING (customer_id = (SELECT app_customer_id())
           OR provider_id = (SELECT app_provider_id())
           OR (SELECT app_is_admin()));

-- A new booking is always a pending request; it can't be created already completed (and then reviewed).
CREATE POLICY bookings_create ON bookings FOR INSERT
    WITH CHECK (customer_id = (SELECT app_customer_id()) AND status = 'pending');

-- A customer may only cancel their own booking; accepting, starting and completing are the provider's.
-- The status-rules trigger still decides which moves are allowed at all. Nobody can move a booking to
-- someone else.
CREATE POLICY bookings_customer_cancels ON bookings FOR UPDATE
    USING (customer_id = (SELECT app_customer_id()))
    WITH CHECK (customer_id = (SELECT app_customer_id()) AND status = 'cancelled');

CREATE POLICY bookings_provider_updates ON bookings FOR UPDATE
    USING (provider_id = (SELECT app_provider_id()))
    WITH CHECK (provider_id = (SELECT app_provider_id()));

-- ── Rows that belong to a booking: visible to whoever can see the booking ─
-- The EXISTS reads bookings, so the bookings policy above decides.
CREATE POLICY booking_items_read ON booking_items FOR SELECT
    USING (EXISTS (SELECT 1 FROM bookings b WHERE b.booking_id = booking_items.booking_id));

CREATE POLICY booking_items_create ON booking_items FOR INSERT
    WITH CHECK (EXISTS (SELECT 1 FROM bookings b
                        WHERE b.booking_id = booking_items.booking_id
                          AND b.customer_id = (SELECT app_customer_id())));

CREATE POLICY booking_status_log_read ON booking_status_log FOR SELECT
    USING (EXISTS (SELECT 1 FROM bookings b WHERE b.booking_id = booking_status_log.booking_id));

CREATE POLICY payments_read ON payments FOR SELECT
    USING (EXISTS (SELECT 1 FROM bookings b WHERE b.booking_id = payments.booking_id));

CREATE POLICY payments_create ON payments FOR INSERT
    WITH CHECK (EXISTS (SELECT 1 FROM bookings b
                        WHERE b.booking_id = payments.booking_id
                          AND b.customer_id = (SELECT app_customer_id())));

-- Refunds are created by sp_cancel_booking, for whoever cancels, and processed by admins.
CREATE POLICY refunds_read ON refunds FOR SELECT
    USING (EXISTS (SELECT 1 FROM payments p WHERE p.payment_id = refunds.payment_id));

CREATE POLICY refunds_create ON refunds FOR INSERT
    WITH CHECK (EXISTS (SELECT 1 FROM payments p WHERE p.payment_id = refunds.payment_id));

CREATE POLICY refunds_process ON refunds FOR UPDATE
    USING ((SELECT app_is_admin()))
    WITH CHECK ((SELECT app_is_admin()));

-- A cancellation records the person who cancelled; nobody can record it in someone else's name.
CREATE POLICY cancellations_read ON cancellations FOR SELECT
    USING (EXISTS (SELECT 1 FROM bookings b WHERE b.booking_id = cancellations.booking_id));

CREATE POLICY cancellations_create ON cancellations FOR INSERT
    WITH CHECK (cancelled_by = (SELECT app_user_id())
                AND EXISTS (SELECT 1 FROM bookings b WHERE b.booking_id = cancellations.booking_id));

-- Readable and removable by anyone who can see the booking, so a provider who cancels
-- gives the customer's coupon use back.
CREATE POLICY coupon_redemptions_read ON coupon_redemptions FOR SELECT
    USING (EXISTS (SELECT 1 FROM bookings b WHERE b.booking_id = coupon_redemptions.booking_id));

CREATE POLICY coupon_redemptions_create ON coupon_redemptions FOR INSERT
    WITH CHECK (customer_id = (SELECT app_customer_id())
                AND EXISTS (SELECT 1 FROM bookings b
                            WHERE b.booking_id = coupon_redemptions.booking_id
                              AND b.customer_id = (SELECT app_customer_id())));

CREATE POLICY coupon_redemptions_release ON coupon_redemptions FOR DELETE
    USING (EXISTS (SELECT 1 FROM bookings b WHERE b.booking_id = coupon_redemptions.booking_id));

-- ── A user's own rows ───────────────────────────────────────
CREATE POLICY idempotency_keys_own ON idempotency_keys
    USING (user_id = (SELECT app_user_id()))
    WITH CHECK (user_id = (SELECT app_user_id()));

CREATE POLICY customer_addresses_own ON customer_addresses
    USING (customer_id = (SELECT app_customer_id()))
    WITH CHECK (customer_id = (SELECT app_customer_id()));

-- Raised by a person on the booking, in their own name; handled by admins.
CREATE POLICY complaints_read ON complaints FOR SELECT
    USING (raised_by = (SELECT app_user_id()) OR (SELECT app_is_admin()));

CREATE POLICY complaints_create ON complaints FOR INSERT
    WITH CHECK (raised_by = (SELECT app_user_id())
                AND EXISTS (SELECT 1 FROM bookings b WHERE b.booking_id = complaints.booking_id));

CREATE POLICY complaints_handle ON complaints FOR UPDATE
    USING ((SELECT app_is_admin()))
    WITH CHECK ((SELECT app_is_admin()));

CREATE POLICY provider_documents_read ON provider_documents FOR SELECT
    USING (provider_id = (SELECT app_provider_id()) OR (SELECT app_is_admin()));

CREATE POLICY provider_documents_verify ON provider_documents FOR UPDATE
    USING ((SELECT app_is_admin()))
    WITH CHECK ((SELECT app_is_admin()));

-- ── Public profiles: everyone reads, only the right person writes ─
CREATE POLICY service_providers_read ON service_providers FOR SELECT
    USING (true);

-- Registering creates your own profile only.
CREATE POLICY service_providers_register ON service_providers FOR INSERT
    WITH CHECK (user_id = (SELECT app_user_id()));

-- Only admins change a profile (verification). The rating cache is kept by a SECURITY DEFINER trigger.
CREATE POLICY service_providers_verify ON service_providers FOR UPDATE
    USING ((SELECT app_is_admin()))
    WITH CHECK ((SELECT app_is_admin()));

CREATE POLICY provider_services_read ON provider_services FOR SELECT
    USING (true);

CREATE POLICY provider_services_add ON provider_services FOR INSERT
    WITH CHECK (provider_id = (SELECT app_provider_id()));

CREATE POLICY provider_services_change ON provider_services FOR UPDATE
    USING (provider_id = (SELECT app_provider_id()))
    WITH CHECK (provider_id = (SELECT app_provider_id()));

CREATE POLICY provider_availability_read ON provider_availability FOR SELECT
    USING (true);

CREATE POLICY provider_availability_add ON provider_availability FOR INSERT
    WITH CHECK (provider_id = (SELECT app_provider_id()));

CREATE POLICY provider_availability_remove ON provider_availability FOR DELETE
    USING (provider_id = (SELECT app_provider_id()));

-- Reviews are public; only the booking's customer writes one (a trigger checks it's completed).
CREATE POLICY provider_reviews_read ON provider_reviews FOR SELECT
    USING (true);

CREATE POLICY provider_reviews_write ON provider_reviews FOR INSERT
    WITH CHECK (EXISTS (SELECT 1 FROM bookings b
                        WHERE b.booking_id = provider_reviews.booking_id
                          AND b.customer_id = (SELECT app_customer_id())));

CREATE POLICY service_reviews_read ON service_reviews FOR SELECT
    USING (true);

CREATE POLICY service_reviews_write ON service_reviews FOR INSERT
    WITH CHECK (EXISTS (SELECT 1 FROM bookings b
                        WHERE b.booking_id = service_reviews.booking_id
                          AND b.customer_id = (SELECT app_customer_id())));
