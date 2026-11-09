-- Task 2.7: valid discounts, and a usage limit that is actually enforced.

ALTER TABLE coupons
    ADD COLUMN max_discount NUMERIC(10,2) CHECK (max_discount > 0),
    ADD COLUMN used_count   INT NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    ADD CONSTRAINT chk_coupon_percentage CHECK (discount_type <> 'percentage' OR discount_value <= 100);

UPDATE coupons c SET used_count = (SELECT COUNT(*) FROM bookings b WHERE b.coupon_id = c.coupon_id);

ALTER TABLE coupons
    ADD CONSTRAINT chk_coupon_usage_limit CHECK (usage_limit IS NULL OR used_count <= usage_limit);
