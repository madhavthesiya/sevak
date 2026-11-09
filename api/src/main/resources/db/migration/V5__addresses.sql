-- Task 2.4: a location is a building or point; the flat number depends on
-- (customer, location). Two flats in one building now share one locations row.

ALTER TABLE locations
    ALTER COLUMN latitude  SET NOT NULL,
    ALTER COLUMN longitude SET NOT NULL;

ALTER TABLE customer_addresses
    ADD COLUMN house_no  VARCHAR(50),
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- A booking must happen at one of that customer's own addresses.
-- Addresses used by bookings are soft-deleted (is_active), never removed.
ALTER TABLE bookings
    ADD CONSTRAINT fk_booking_customer_address
    FOREIGN KEY (customer_id, location_id) REFERENCES customer_addresses (customer_id, location_id);

-- The areas a provider covers. service_providers.city_id stays as their home city.
CREATE TABLE provider_service_areas (
    provider_id INT NOT NULL REFERENCES service_providers (provider_id) ON DELETE CASCADE,
    area_id     INT NOT NULL REFERENCES areas (area_id) ON DELETE CASCADE,
    PRIMARY KEY (provider_id, area_id)
);
CREATE INDEX idx_provider_areas_area ON provider_service_areas (area_id);

INSERT INTO provider_service_areas (provider_id, area_id)
SELECT sp.provider_id, a.area_id
FROM service_providers sp JOIN areas a ON a.city_id = sp.city_id;
