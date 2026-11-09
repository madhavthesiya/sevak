-- Task 2.3: a price belongs to a (provider, service) pair, not to the provider.

ALTER TABLE provider_services
    ADD COLUMN price     NUMERIC(10,2) CHECK (price >= 0),
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- The old custom_price was one number across unrelated services, so it can't be
-- carried over per service; the catalogue price is the sensible starting point.
UPDATE provider_services ps SET price = s.base_price
FROM services s WHERE s.service_id = ps.service_id;

ALTER TABLE provider_services ALTER COLUMN price SET NOT NULL;
ALTER TABLE service_providers DROP COLUMN custom_price;

-- A booking item's variant must belong to that item's service.
ALTER TABLE service_variants ADD CONSTRAINT uq_variant_service UNIQUE (service_id, variant_id);
ALTER TABLE booking_items
    ADD CONSTRAINT fk_item_variant_matches_service
    FOREIGN KEY (service_id, variant_id) REFERENCES service_variants (service_id, variant_id);

ALTER TABLE cities ADD CONSTRAINT uq_cities_name_state UNIQUE (city_name, state);
ALTER TABLE areas  ADD CONSTRAINT uq_areas_city_name   UNIQUE (city_id, area_name);
