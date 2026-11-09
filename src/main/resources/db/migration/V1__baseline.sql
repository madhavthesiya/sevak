-- ============================================================
--  SEVAK: baseline schema (V1)
--  Flyway creates the "sevak" schema and runs this inside it.
--  Never edit this file after it has been merged; add a new
--  V<n>__description.sql migration instead.
-- ============================================================

-- ============================================================
-- 1. USERS
-- ============================================================
CREATE TABLE users (
    user_id    SERIAL       PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL CHECK (role IN ('customer','provider','admin')),
    status     VARCHAR(20)  NOT NULL DEFAULT 'active'
                            CHECK (status IN ('active','inactive','banned')),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 2. CUSTOMERS
-- ============================================================
CREATE TABLE customers (
    customer_id SERIAL       PRIMARY KEY,
    user_id     INT          NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    phone       VARCHAR(20)
);

-- ============================================================
-- 3. CITIES
-- ============================================================
CREATE TABLE cities (
    city_id   SERIAL       PRIMARY KEY,
    city_name VARCHAR(100) NOT NULL,
    state     VARCHAR(100) NOT NULL
);

-- ============================================================
-- 4. SERVICE PROVIDERS
-- ============================================================
CREATE TABLE service_providers (
    provider_id         SERIAL         PRIMARY KEY,
    user_id             INT            NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    city_id             INT            NOT NULL REFERENCES cities(city_id),
    bio                 TEXT,
    experience_years    INT            CHECK (experience_years >= 0),
    avg_rating          NUMERIC(3,2)   CHECK (avg_rating BETWEEN 0 AND 5),
    verification_status VARCHAR(20)    NOT NULL DEFAULT 'pending'
                                       CHECK (verification_status IN ('pending','verified','rejected')),
    custom_price        NUMERIC(10,2),
    is_active           BOOLEAN        NOT NULL DEFAULT TRUE
);

-- ============================================================
-- 5. ADMINS
-- ============================================================
CREATE TABLE admins (
    admin_id    SERIAL       PRIMARY KEY,
    user_id     INT          NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    admin_role  VARCHAR(50)  NOT NULL,
    permissions TEXT,
    department  VARCHAR(100)
);

-- ============================================================
-- 6. AREAS
-- ============================================================
CREATE TABLE areas (
    area_id   SERIAL       PRIMARY KEY,
    city_id   INT          NOT NULL REFERENCES cities(city_id),
    area_name VARCHAR(100) NOT NULL,
    pincode   VARCHAR(10)  NOT NULL
);

-- ============================================================
-- 7. LOCATIONS
-- (Physical location data — BCNF: {lat, lng} → {area, street, landmark})
-- ============================================================
CREATE TABLE locations (
    location_id SERIAL PRIMARY KEY,
    area_id     INT NOT NULL REFERENCES areas(area_id),
    street      VARCHAR(255) NOT NULL,
    landmark    VARCHAR(255),
    latitude    NUMERIC(10,7),
    longitude   NUMERIC(10,7),
    UNIQUE (latitude, longitude)
);

-- ============================================================
-- 8. CUSTOMER ADDRESSES
-- (Maps customers to locations with a label — BCNF compliant)
-- ============================================================
CREATE TABLE customer_addresses (
    customer_id INT NOT NULL REFERENCES customers(customer_id) ON DELETE CASCADE,
    location_id INT NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    label       VARCHAR(50),
    PRIMARY KEY (customer_id, location_id)
);

-- ============================================================
-- 9. CATEGORIES
-- ============================================================
CREATE TABLE categories (
    category_id   SERIAL       PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE,
    description   TEXT
);

-- ============================================================
-- 9. SERVICES
-- ============================================================
CREATE TABLE services (
    service_id   SERIAL         PRIMARY KEY,
    category_id  INT            NOT NULL REFERENCES categories(category_id),
    service_name VARCHAR(150)   NOT NULL,
    base_price   NUMERIC(10,2)  NOT NULL CHECK (base_price >= 0),
    description  TEXT
);

-- ============================================================
-- 10. SERVICE VARIANTS  (Weak entity of Service)
-- ============================================================
CREATE TABLE service_variants (
    variant_id   SERIAL         PRIMARY KEY,
    service_id   INT            NOT NULL REFERENCES services(service_id) ON DELETE CASCADE,
    variant_name VARCHAR(100)   NOT NULL,
    price        NUMERIC(10,2)  NOT NULL CHECK (price >= 0),
    duration     INT            CHECK (duration > 0),   -- minutes
    UNIQUE (service_id, variant_name)
);

-- ============================================================
-- 11. PROVIDER SERVICES (M:N Offers Table - FIXED)
-- ============================================================
CREATE TABLE provider_services (
    provider_id INT NOT NULL REFERENCES service_providers(provider_id) ON DELETE CASCADE,
    service_id  INT NOT NULL REFERENCES services(service_id) ON DELETE CASCADE,
    PRIMARY KEY (provider_id, service_id)
);

-- ============================================================
-- 12. COUPONS
-- ============================================================
CREATE TABLE coupons (
    coupon_id      SERIAL        PRIMARY KEY,
    code           VARCHAR(30)   NOT NULL UNIQUE,
    discount_type  VARCHAR(20)   NOT NULL CHECK (discount_type IN ('percentage','flat')),
    discount_value NUMERIC(10,2) NOT NULL CHECK (discount_value > 0),
    min_order      NUMERIC(10,2) DEFAULT 0,
    usage_limit    INT,
    valid_from     DATE          NOT NULL,
    valid_to       DATE          NOT NULL,
    CHECK (valid_to >= valid_from)
);

-- ============================================================
-- 13. BOOKINGS
-- ============================================================
CREATE TABLE bookings (
    booking_id            SERIAL        PRIMARY KEY,
    customer_id           INT           NOT NULL REFERENCES customers(customer_id),
    provider_id           INT           NOT NULL REFERENCES service_providers(provider_id),
    location_id           INT           NOT NULL REFERENCES locations(location_id),
    coupon_id             INT           REFERENCES coupons(coupon_id),
    scheduled_date        DATE          NOT NULL,
    scheduled_time        TIME          NOT NULL,
    status                VARCHAR(30)   NOT NULL DEFAULT 'pending'
                                        CHECK (status IN ('pending','confirmed','in_progress',
                                                          'completed','cancelled')),
    total_amount          NUMERIC(10,2) NOT NULL CHECK (total_amount >= 0),
    special_instructions  TEXT
);

-- ============================================================
-- 14. BOOKING ITEMS  (Weak entity of Booking)
-- ============================================================
CREATE TABLE booking_items (
    booking_id INT           NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
    item_no    INT           NOT NULL,
    service_id INT           NOT NULL REFERENCES services(service_id),
    variant_id INT           REFERENCES service_variants(variant_id),
    quantity   INT           NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (booking_id, item_no)
);

-- ============================================================
-- 15. BOOKING STATUS LOG  (Weak entity of Booking)
-- ============================================================
CREATE TABLE booking_status_log (
    booking_id INT          NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
    log_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    status     VARCHAR(30)  NOT NULL,
    remarks    TEXT,
    PRIMARY KEY (booking_id, log_time)
);

-- ============================================================
-- 16. PAYMENTS  (1:1 with Booking)
-- ============================================================
CREATE TABLE payments (
    payment_id     SERIAL        PRIMARY KEY,
    booking_id     INT           NOT NULL UNIQUE REFERENCES bookings(booking_id),
    payment_method VARCHAR(50)   NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'pending'
                                 CHECK (status IN ('pending','success','failed','refunded')),
    amount         NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    gateway_ref    VARCHAR(255),
    paid_at        TIMESTAMP
);

-- ============================================================
-- 17. CANCELLATIONS  (1:1 with Booking)
-- ============================================================
CREATE TABLE cancellations (
    cancel_id     SERIAL      PRIMARY KEY,
    booking_id    INT         NOT NULL UNIQUE REFERENCES bookings(booking_id),
    reason        TEXT        NOT NULL,
    refund_status VARCHAR(20) NOT NULL DEFAULT 'pending'
                              CHECK (refund_status IN ('pending','processed','not_applicable'))
);

-- ============================================================
-- 18. PROVIDER DOCUMENTS  (Weak entity of ServiceProvider)
-- ============================================================
CREATE TABLE provider_documents (
    document_id   SERIAL       PRIMARY KEY,
    provider_id   INT          NOT NULL REFERENCES service_providers(provider_id) ON DELETE CASCADE,
    document_type VARCHAR(100) NOT NULL,
    file_url      VARCHAR(500) NOT NULL,
    uploaded_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    description   TEXT
);

-- ============================================================
-- 19. PROVIDER AVAILABILITY  (Weak entity of ServiceProvider)
-- ============================================================
CREATE TABLE provider_availability (
    provider_id  INT         NOT NULL REFERENCES service_providers(provider_id) ON DELETE CASCADE,
    day_of_week  VARCHAR(10) NOT NULL CHECK (day_of_week IN
                             ('Monday','Tuesday','Wednesday','Thursday',
                              'Friday','Saturday','Sunday')),
    start_time   TIME        NOT NULL,
    end_time     TIME        NOT NULL,
    CHECK (end_time > start_time),
    PRIMARY KEY (provider_id, day_of_week)
);

-- ============================================================
-- 20. PROVIDER REVIEWS  (Weak entity — tied to Booking)
-- ============================================================
CREATE TABLE provider_reviews (
    review_id  SERIAL  PRIMARY KEY,
    booking_id INT     NOT NULL UNIQUE REFERENCES bookings(booking_id),
    rating     INT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    TEXT
);

-- ============================================================
-- 21. SERVICE REVIEWS  (Weak entity — tied to Booking)
-- ============================================================
CREATE TABLE service_reviews (
    review_id   SERIAL PRIMARY KEY,
    service_id  INT    NOT NULL REFERENCES services(service_id),
    booking_id  INT    NOT NULL REFERENCES bookings(booking_id),
    rating      INT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    UNIQUE (service_id, booking_id)
);

-- ============================================================
-- 22. COMPLAINTS (Fixed: strictly 3NF)
-- ============================================================
CREATE TABLE complaints (
    complaint_id INT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id   INT          NOT NULL REFERENCES bookings(booking_id),
    subject      VARCHAR(255) NOT NULL,
    status       VARCHAR(30)  NOT NULL DEFAULT 'open'
                              CHECK (status IN ('open','in_progress','resolved','closed'))
);

-- ============================================================
--  INDEXES
-- ============================================================
CREATE INDEX idx_bookings_customer   ON bookings(customer_id);
CREATE INDEX idx_bookings_provider   ON bookings(provider_id);
CREATE INDEX idx_bookings_location   ON bookings(location_id);
CREATE INDEX idx_bookings_status     ON bookings(status);
CREATE INDEX idx_bookings_date       ON bookings(scheduled_date);
CREATE INDEX idx_services_category   ON services(category_id);
CREATE INDEX idx_areas_city          ON areas(city_id);
CREATE INDEX idx_locations_area      ON locations(area_id);
CREATE INDEX idx_cust_addr_customer  ON customer_addresses(customer_id);
CREATE INDEX idx_prov_docs_provider  ON provider_documents(provider_id);
CREATE INDEX idx_svc_reviews_svc     ON service_reviews(service_id);
CREATE INDEX idx_complaints_status   ON complaints(status);

