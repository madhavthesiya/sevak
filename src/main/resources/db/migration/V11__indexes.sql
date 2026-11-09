-- Task 2.11: indexes for the joins and lists the API actually runs.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_provider_services_service ON provider_services (service_id);
CREATE INDEX idx_complaints_booking        ON complaints (booking_id);
CREATE INDEX idx_providers_city            ON service_providers (city_id);
CREATE INDEX idx_booking_items_service     ON booking_items (service_id);
CREATE INDEX idx_availability_day          ON provider_availability (day_of_week);
CREATE INDEX idx_refunds_payment           ON refunds (payment_id);
CREATE INDEX idx_bookings_customer_start   ON bookings (customer_id, scheduled_start DESC);
CREATE INDEX idx_bookings_provider_status  ON bookings (provider_id, status, scheduled_start);
CREATE INDEX idx_bookings_pending          ON bookings (provider_id, scheduled_start) WHERE status = 'pending';
CREATE INDEX idx_services_name_trgm        ON services USING gin (service_name gin_trgm_ops);

-- Covered by the composite indexes above or by a primary key.
DROP INDEX idx_bookings_customer;
DROP INDEX idx_bookings_provider;
DROP INDEX idx_bookings_status;
DROP INDEX idx_cust_addr_customer;
